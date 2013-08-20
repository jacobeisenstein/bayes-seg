package edu.mit.nlp.segmenter.dp;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import edu.mit.util.stats.*;
//not sure if i'll include this in the distro
import edu.mit.util.weka.LBFGSWrapper; 
import edu.mit.nlp.segmenter.*;
import riso.numerical.LBFGS;
import edu.mit.util.JacobUtil;

/**
   This class implements the dynamic programming Bayesian segmentation, for
   both DCM and MAP language models.  

   <P> Now with EM estimation of priors.
   Note that we use log-priors everywhere.  The reason is that the log of the
   prior is in [-inf,inf], while the prior itself is in [0,inf].  Since my
   LBFGS engine doesn't take constraints, it's better to search in log space.
   This requires only a small modification to the gradient computation.
**/

public class DPSeg {
    /**
       @param docs The documents to segment.  It's a 2D array for the multimodal segmentation case,
       but if you're just doing text then it will be [N][1].  
       @param truths The ground truth segmentations.  [N][], with each row being an another array of ints.
       I'd like to refactor so that this isn't necessary, but at the moment it is.
    **/
    public DPSeg(DPDocument[][] docs, int[][] truths){
        m_docs = docs;
        N = docs.length;
        M = docs[0].length;
        m_truths = truths;
        m_segs = new int[N][];
        m_K = new int[N];
        for (int i = 0; i < truths.length; i++){
            m_K[i] = truths[i][truths[i].length-1];
            m_segs[i] = new int[m_K[i]];
        }
        m_debug = false;
        m_digamma = new FastDigamma();
  
        int biggest_sum = 0;
        int start_size = 0;
        for (int docctr = 0; docctr < N; docctr++){
            biggest_sum = 0;
            for (int d = 0; d < m_docs[docctr].length; d++){
                for (int i = 0; i < m_docs[docctr][d].cumSum.length; i++){
                    if (m_docs[docctr][d].cumSum[i] > biggest_sum) 
                        biggest_sum = (int) m_docs[docctr][d].cumSum[i];
                }
            }
            start_size += biggest_sum;
        }
        if (m_debug) System.out.println("Hash start size: "+start_size*2);
        m_fast_gamma = new FastDoubleGamma(start_size*2,.6f); //this sets the hash parameters
        m_num_segments_known = true;
    }    

    /**
       A class for LBFGS optimization of the priors
    **/
    protected class PriorOptimizer extends LBFGSWrapper {
        public PriorOptimizer(){
            super(m_docs[0].length+1);
        }
        //we're doing the argmin, so invert it
        public double objectiveFunction(double[] params) {
            return -computeTotalLL(params);
        }
        public double[] evaluateGradient(double[] params){
            double[] out = computeGradient(params);
            for (int i = 0; i < out.length; i++) out[i] = -out[i];
            //for (int i = 0; i < out.length - 1; i++) out[i] = 0;
            //out[out.length-1] = 0;
            return out;
        }
    }

    
    /**
       segEM estimates the parameters using a form of hard EM
       it computes the best segmentation given the current parameters, 
       then does a gradient-based search for new parameters,
       and iterates.
       
       As an argument it takes the initial settings, in log terms.

       one idea of how to speed this up is to only recompute the segmentation for a subset of files.
       or, just call segem on a few files, then call the final segmentation on all of them.
       we could add a class member variable indicating "active" files, and then only 
       apply segment(), computeGradient(), and computeLL() to those files.
       by default all files would be active.
    **/
    public SegResult[] segEM(double[] init_params){
        m_params = new double[init_params.length];
        for (int i = 0; i < init_params.length; i++) m_params[i] = init_params[i];
        SegResult[] results = segment(m_params);
        PriorOptimizer opt = new PriorOptimizer();
        opt.m_eps = 1e-5; opt.m_num_corrections = 6; //can afford this because it's relatively easy optimization
        int ctr = 0;
        double improvement = Double.MAX_VALUE;
        double old_ll = Double.MAX_VALUE;
        //scan parameters
        double cur_log_dispersion = m_params[m_params.length-1];
        for (double d= cur_log_dispersion-5; d <= cur_log_dispersion+5; d+= .5){
            m_params[m_params.length-1] = d;
            double[] gradient  = computeGradient(m_params);
            System.out.println(computeTotalLL(m_params)+" "+
                               m_params[1]+" "+
                               gradient[1]);
        }
        

        do {
            opt.setEstimate(m_params);
            opt.setMaxIteration(200);
            opt.setDebug(m_debug);
            try {
                m_params = opt.findArgmin();
            } catch (Exception e){
                //e.printStackTrace();
            }
            m_params = opt.getVarbValues();
            results = segment(m_params);
            //SegResult.printResults(results,params);
            improvement = old_ll - opt.getMinFunction();
            old_ll = opt.getMinFunction();
            if (m_debug) System.out.println(String.format("delta: %.2e, ctr: %d, score: %.2e, params: %s",
                                                          Math.log(improvement),ctr,opt.getMinFunction(),
                                                          JacobUtil.formatArray("%.1e"," ",m_params)));
        } while (improvement > 0 && ++ctr < 20);
        //        System.out.println("BEST PARAMS: "+JacobUtil.formatArray("%.3f"," ",m_params));
        //        printSegs();
        //        SegResult.printResults(results,params);
        return results;
    }

    public void printSegs(){
        System.out.println("---- segmentation ----");
        for (int i =0 ;i < m_segs.length; i++){
            for (int j =0; j < m_segs[i].length; j++){
                System.out.print(m_segs[i][j]+" ");
            }
            System.out.println();
        }
    }

    protected double[] computePDur(int T, double edur, double log_dispersion){
        double pdur[] = new double[(int)T+1];
        if (log_dispersion > MAX_LOG_DISPERSION) log_dispersion = MAX_LOG_DISPERSION;
        for (int i =0 ; i <= T; i++){
            pdur[i] = Stats.myLogNBinPdf2(i,edur,Math.exp(log_dispersion));
        }
        return pdur;
    }

    /**
       segment each document in the dataset.  
       
      @param params the (log) parameters
       the last entry in the input array
       is the log of the dispersion parameter for the duration distribution.  
       the other ones are the logs of the priors (for each modality)

       @return the results for each document.  kind of a bad design, it ought to just return the segmentation.
       

    **/
    public SegResult[] segment(double[] params){
        return m_num_segments_known?segmentKnown(params):segmentUnknown(params);
    }
    
    /**
       segment in the case of an unknown number of segments. same arguments as {@link #segment(double[])}
    */
    protected SegResult[] segmentUnknown(double[] params){
        SegResult[] output = new SegResult[N];
        double priors[] = new double[m_docs[0].length];
        for (int i = 0; i < m_docs[0].length; i++) priors[i] = Math.exp(params[i]);

        //expected duration is average over the whole dataset
        double total_num_sent=0;
        double total_num_segs=0;        
        for (int docctr = 0; docctr < N; docctr++){
            total_num_sent += m_docs[docctr][0].T();
            total_num_segs += m_K[docctr];
        }
        double expected_dur = total_num_sent / total_num_segs;

        for (int docctr = 0; docctr < N; docctr++){
            DPDocument[] docs = m_docs[docctr];
            int T = (int) docs[0].T(); //number of sentences
            
            for (int d = 0; d < docs.length; d++){
                if (docs[d].m_dcm) docs[d].setGamma(m_fast_gamma);
                docs[d].setPrior(priors[d]);
            }
            for (int d = 1; d < docs.length; d++) assert (docs[d].T()==T); 
            assert (docs.length == params.length - 1);

            //this is the DP
            double C[] = new double[T+1]; //cost of best segmentation up to T
            int B[] = new int[T+1]; //index of prev segmentation point
            double[] pdur = computePDur(T,expected_dur,params[params.length-1]);
            if (!use_duration) pdur = null;
            double[][] seglls = new double[T+1][T+1];
            for (int t = 0; t <= T; t++){
                for (int t2 = 0; t2 < t; t2++){
                    for (int d =0; d < docs.length; d++){
                        seglls[t][t2] += docs[d].segLL(t2+1,t,priors[d]);
                    }
                }
            }

            C[0] = 0; B[0] = 0;
            for (int t = 1; t <= T; t++){
                //C[t] = max_t2 C[t2] + ll(x_t2+1 ... x_t)
                //B[t] = argmax of above
                double best_val = -Double.MAX_VALUE; int best_idx = -1;
                for (int t2 = 0; t2 < t; t2++){
                    double score = C[t2] + seglls[t][t2];
                    if (pdur != null) score = score + pdur[t-t2];
                    if (score > best_val){ best_val = score; best_idx = t2; }
                }
                C[t] = best_val; B[t] = best_idx;
            }
            int num_segs = 1; int next_seg_pt = B[B.length-1];
            List<Integer> segpts = new ArrayList<Integer>();
            while (next_seg_pt > 0){
                segpts.add(next_seg_pt);
                next_seg_pt = B[next_seg_pt];
            }
            Collections.sort(segpts);
            m_segs[docctr] = new int[segpts.size()];
            for (int i = 0; i < segpts.size(); i++) m_segs[docctr][i] = segpts.get(i);
            output[docctr] = new SegResult(m_segs[docctr],m_truths[docctr],C[T]);
        }
        return output;            
    }

    /**
       segment in the case that the number of segments per doc is known. same arguments as {@link #segment(double[])}
    */
    protected SegResult[] segmentKnown(double[] params){ 
        SegResult[] output = new SegResult[N];
        //initial size should be the size of the largest sum * 3 * 2 ?
  
        //we have the log priors passed in as arguments. exponentiate them now
        double priors[] = new double[m_docs[0].length];
        for (int i = 0; i < m_docs[0].length; i++)
            priors[i] = Math.exp(params[i]);
        if (m_debug) System.out.println("Params: "+JacobUtil.formatArray("%.4f"," ",priors)+" "+Math.exp(params[params.length-1]));

        for (int docctr = 0; docctr < N; docctr++){
            DPDocument[] docs = m_docs[docctr];
            for (int d = 0; d < docs.length; d++){
                if (docs[d].m_dcm) docs[d].setGamma(m_fast_gamma);
                docs[d].setPrior(priors[d]);
            }
            int[] truth = m_truths[docctr];
            
            int K = m_K[docctr];
            int T = (int) docs[0].T(); //number of sentences
            for (int d = 1; d < docs.length; d++) assert (docs[d].T()==T); 
            //assert (docs.length == params.length - 1);

            //this is the DP
            double C[][] = new double[K+1][T+1];
            int B[][] = new int[K+1][T+1];
            
            double[] pdur = computePDur(T,(double)T / K,params[params.length-1]);
            if (! use_duration) pdur = null;
            //this checks out ok
//             System.out.print(String.format("%d %d %.3f ",T,K,Math.exp(params[params.length-1])));
//             for (int i = 0; i < pdur.length; i++){
//                 System.out.print(String.format("%.3f ",pdur[i]));
//             }
//             System.out.println();

            //the semantics of C are:
            //C[i][t] = the ll of the best segmentation of x_0 .. x_[t-1] into i segments
            //initialize
            
            double[][] seglls = new double[T+1][T+1];
            for (int t = 0; t <= T; t++){
                for (int t2 = 0; t2 < t; t2++){
                    for (int d =0; d < docs.length; d++){
                        seglls[t][t2] += docs[d].segLL(t2+1,t,priors[d]);
                    }
                }
            }
            C[0][0] = 0; B[0][0] = 0;
            for (int t = 1; t <= T; t++){
                C[0][t] = -Double.MAX_VALUE; B[0][t] = 1;
            }
            for (int i = 1; i <= K; i++){
                for (int t = 0; t < i; t++){
                    C[i][t] = -Double.MAX_VALUE;
                    B[i][t] = -1;
                }
                for (int t = i; t <= T; t++){
                    //C[i][t] = max_t2 C[i-1][t2] + ll(x_t2+1 .. x_t)
                    double best_val = -Double.MAX_VALUE; int best_idx = -1;
                    for (int t2 = 0; t2 < t; t2++){
                        double score = C[i-1][t2] + seglls[t][t2];
                        if (pdur != null) score = score + pdur[t-t2];
                        if (score > best_val){
                            best_val = score; best_idx = t2;
                        }
                    }
                    C[i][t] = best_val; B[i][t] = best_idx;
                }
            }

            m_segs[docctr][K-1] = B[K][T];
            for (int k = K - 1; k > 0; k--) m_segs[docctr][k-1] = B[k][m_segs[docctr][k]];
            output[docctr] = new SegResult(m_segs[docctr],truth,C[K][T]);
        }
        return output;
    }

    /**
       compute the loglikelihood for the whole dataset.  useful for reestimating priors
    **/
    public double computeTotalLL(double[] logpriors){
        //        if (m_debug) System.out.print("computing LL for "+JacobUtil.formatArray("%.3f"," ",logpriors)+" ");
        double out = 0;
        double log_dispersion = logpriors[logpriors.length-1];
        double priors[] = new double[m_docs[0].length];
        for (int i = 0; i < m_docs[0].length; i++)
            priors[i] = Math.exp(logpriors[i]);
        for (int docctr = 0; docctr < N; docctr++){
            for (int d = 0; d < m_docs[docctr].length; d++){
                m_docs[docctr][d].setGamma(m_fast_gamma);
                m_docs[docctr][d].setPrior(priors[d]);
            }
            int[] truths = m_truths[docctr];
            int T = (int)m_docs[docctr][0].T();
            int K = m_segs[docctr].length;  //m_truths[docctr][m_truths[docctr].length-1];
            //   System.out.println(String.format("T:%d K:%d",T,K));
            double[] pdur = computePDur(T,(double)T/m_K[docctr],log_dispersion);
            for (int k = 0; k < K-1; k++){
                if (use_duration) out += pdur[m_segs[docctr][k+1] - m_segs[docctr][k]];
                for (int d = 0; d < m_docs[docctr].length; d++){
                    out += m_docs[docctr][d].segLLExp(m_segs[docctr][k]+1,m_segs[docctr][k+1],logpriors[d]);
                    //                    System.out.println (" "+k+" "+m_docs[docctr][d].segLLExp(m_segs[docctr][k]+1,m_segs[docctr][k+1],logpriors[d]));
                }
            }
            if (use_duration) out += pdur[T-m_segs[docctr][K-1]];
            for (int d = 0; d < m_docs[docctr].length; d++){
                out += m_docs[docctr][d].segLLExp(m_segs[docctr][K-1]+1,(int)m_docs[docctr][0].T(),logpriors[d]);
                //System.out.println (" "+(K-1)+" "+m_docs[docctr][d].segLLExp(m_segs[docctr][K-1]+1,(int)m_docs[docctr][0].T(),logpriors[d]));
            }
        }
        //factor in the IG(1,1) prior on the dispersion parameter
        //out += -(ALPHA + 1) * log_dispersion - BETA / Math.exp(log_dispersion);
        //        if (m_debug) System.out.println(out);        
        return out;
    }

    /**
       computes the gradient of the likelihood, across the whole dataset.
    **/
    public double[] computeGradient(double[] logpriors){
        //        System.out.println("computing Grad for "+logpriors[logpriors.length-1]+" = ");
        //System.out.print("computing Grad for "+logpriors[0]+" = ");
        int D = m_docs[0].length;
        double out[] = new double[logpriors.length];
        double priors[] = new double[m_docs[0].length];
        for (int i = 0; i < m_docs[0].length; i++)
            priors[i] = Math.exp(logpriors[i]);
        for (int docctr = 0; docctr < N; docctr++){
            for (int d = 0; d < m_docs[docctr].length; d++){
                m_docs[docctr][d].setGamma(m_fast_gamma);
                m_docs[docctr][d].setPrior(priors[d]);
            }
            int T = (int)m_docs[docctr][0].T();
            int K = m_segs[docctr].length;  //m_truths[docctr][m_truths[docctr].length-1];
            for (int k = 0; k < K - 1; k++){
                if (use_duration){
                    out[out.length-1] += Stats.computeDispersionGradient
                        (m_segs[docctr][k+1] - m_segs[docctr][k],(double)T/(double)K,logpriors[logpriors.length-1],m_digamma);
                }
                for (int d = 0; d < D; d++){
                    out[d] += m_docs[docctr][d].segLLGradientExp(m_segs[docctr][k]+1,m_segs[docctr][k+1],logpriors[d]);
                }
            }
            //add in the gradient for the dispersion parameter
            if (use_duration){
                out[out.length-1] += Stats.computeDispersionGradient
                    (T - m_segs[docctr][K-1],(double)T/(double)K,logpriors[logpriors.length-1],m_digamma);
            }
            for (int d = 0; d < D; d++){
                out[d] += m_docs[docctr][d].segLLGradientExp(m_segs[docctr][m_K[docctr]-1]+1,(int)m_docs[docctr][0].T(),logpriors[d]);
            }
        }
        //factor in the prior on the dispersion parameter, which is IG(1,1)
        //out[out.length-1] += -ALPHA - 1 + BETA * Math.exp(-logpriors[logpriors.length-1]);
        //System.out.println(out[out.length-1]);
        return out;
    }

    /**
       get the segmentations 
    **/
    public int[][] getResponses() { return m_segs; } 
    
    DPDocument[][] m_docs;
    int[][] m_truths; //the ground truths
    int[][] m_segs; //the responses
    double[] m_params; public double[] getParams(){ return m_params; }
    int M; //dimensionality
    int N; //number of docs
    int[] m_K; //num segs per doc
    double[] m_pdur;
    FastDigamma m_digamma;
    FastGamma m_fast_gamma;
    boolean m_num_segments_known;
    boolean use_duration = true;
    public boolean m_debug;
    static double ALPHA =1;
    static double BETA = 1;
    static double MAX_LOG_DISPERSION = 10;
}
