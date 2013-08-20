package edu.mit.nlp.segmenter.dp;

import java.util.ArrayList;
import java.lang.Math;
import java.util.Arrays;
import edu.mit.util.stats.*;
import edu.mit.nlp.segmenter.Document;
import edu.mit.util.JacobUtil;

/**
   Extends {@link edu.mit.nlp.segmenter.Document} with some methods
   specifically for the DP implementation of Bayesian segmentation.
   This class does not take advantage of {@link edu.mit.util.ling.CountsManager} 
   because it keeps cumulative counts instead.
**/
public class DPDocument extends Document {
    /**
       @param sents the sentences in the document
       @param N the number of segments.  I forget what you do if this is unknown
       @param dcm whether you're using the DCM distribution (marginalizing the LMs).  I haven't tested it with this set to false in a long time.
    **/
    public DPDocument(double[][] sents, int N, boolean dcm){
        super(sents,N);
        m_dcm = dcm;
        cumSum = new double[(int)T()+1];
        cumSums = new double[(int)T()+1][(int)D()];
        //fill 'em up
        for (int w = 0; w < D(); w++){
            cumSums[0][w] = 0;
        }
        cumSum[0] = 0;
        
        if (dcm) makeCumulCounts();
        else {
            for (int t = 0; t < T(); t++){
                cumSum[t+1] = 0;
                for (int w = 0; w < D(); w++){
                    cumSums[t+1][w] = cumSums[t][w] + (m_words[t][w]>0?1:0);
                    cumSum[t+1] += cumSums[t+1][w];
                }
            }
        }
        int max_word_count = 0;        
        for (int w = 0; w < D(); w++){
            if (cumSums[(int)T()][w] > max_word_count) max_word_count = (int) cumSums[(int)T()][w];
        }
        digamma = new FastDigamma();
        m_int_counts = true;
        fastdcm = new FastDCM((double)1,(int)D(),m_int_counts);
    }

    /**
       If you have multiple documents, you might want to share the cache for the gamma function
       across all documents.  This lets you tell it to use a specific FastGamma cache.
       
       @param fastGamma the caching fastGamma object
    **/
    public void setGamma(FastGamma fastGamma){ fastdcm.setGamma(fastGamma); }
    /**
       @return the FastGamma caching gamma implementation used here
    **/
    public FastGamma getGamma(){ return fastdcm.getGamma(); }
    /**
       If you have multiple documents, you might want to share the cache for the digamma function
       across all documents.  This lets you tell it to use a specific FastDigamma cache.
       
       @param fastDigamma the caching fastDigamma object
    **/
    public void setDigamma(FastDigamma fastDigamma){ this.digamma = fastDigamma; }
    /**
       @return the FastDigamma caching digamma implementation used here
    **/
    public FastDigamma getDigamma(){ return digamma; }
    /**
       @param prior the value of the symmetric Dirichlet prior
    **/
    public void setPrior(double prior){ fastdcm.setPrior(prior); }

    /**
       Builds up the cumulative counts, a representation that facilitates fast computation
       later. 
     **/
    protected void makeCumulCounts(){
        cumSum[0] = 0;
        for (int t = 0; t < T(); t++){
            cumSum[t+1] = 0;
            for (int w = 0; w < D(); w++){
                cumSums[t+1][w] = cumSums[t][w] + m_words[t][w];
                cumSum[t+1] += cumSums[t+1][w];
            }
        }
    }

    /**
       compute the log likelihood of a segment under the DCM model
     
       @param start the index of the first sentence in the segment
       @param end the index of the last sentence in the segment
       @param prior the symmetric dirichlet prior to use

     **/
    protected double segLLDCM(int start, int end, double prior){
        if (prior == 0) return -Double.MAX_VALUE;
        int D = m_words[0].length; // dictionary size?
        double out = 0;
        if (m_int_counts){
            int[] counts = new int[D];
            for (int i = 0; i < D; i++) counts[i] = (int)(cumSums[end][i] - cumSums[start-1][i]);
            out = fastdcm.logDCM(counts);
        } else {
            double[] counts = new double[D];
            for (int i = 0; i < D; i++) counts[i] = cumSums[end][i] - cumSums[start-1][i];
            out = fastdcm.logDCM(counts);
        }
        return out;
    }
    
    /**
       compute the log likelihood of a segment under the MAP language model
     
       @param start the index of the first sentence in the segment
       @param end the index of the last sentence in the segment
       @param prior the symmetric dirichlet prior to use

       this could be sped up by keeping caches of the log partitions and
       the log counts.
     **/
    protected double segLLMAP(int start, int end, double prior){
        double partition = cumSum[end] - cumSum[start-1] + D() * prior;
        double out =- 0;
        double count =0 ;
        //this does not include the ratio of gamma functions from the dirichlet
        //prior, as that is constant for any observations.  thus ll's for different
        //priors are not comparable
        for (int i =0 ;i < D(); i++){
            count = cumSums[end][i] - cumSums[start-1][i] + prior;
            out += (count - 1) * Math.log(count / partition);
        }
        return out;
    }

    /**
       compute the log-likelihood of a segment

       @param start the index of the first sentence in the segment
       @param end the index of the last sentence in the segment
       @param prior the symmetric dirichlet prior to use
    **/
    public double segLL(int start, int end, double prior){
        return m_dcm?segLLDCM(start,end,prior):segLLMAP(start,end,prior);
    }

    /**
       compute the log-likelihood of a segment, given the log of the prior

       @param start the index of the first sentence in the segment
       @param end the index of the last sentence in the segment
       @param logprior the log of the symmetric dirichlet prior to use
    **/
    public double segLLExp(int start, int end, double logprior){
        return segLL(start,end,Math.exp(logprior));
    }

    
    /**
       compute the gradient of the log-likelihood for a segment, under the DCM model
       
       @param start the index of the first sentence in the segment
       @param end the index of the last sentence in the segment
       @param prior the log of the symmetric dirichlet prior to use
    **/
    public double segDCMGradient(int start, int end, double prior){
        if (prior == 0) return Double.MAX_VALUE;
        double out = D() * (digamma.digamma(D()*prior) - digamma.digamma(cumSum[end] - cumSum[start-1] + D()*prior) - digamma.digamma(prior));
//         int D = D();
//         double out = D * digamma.digamma_d_times_prior - digamma.digamma.digamma.digamma(cumSum[end] - cumSum[start-1] + D*prior) - digamma.digamma_prior; 
        for (int i = 0; i < D(); i++)
            out += digamma.digamma(cumSums[end][i] - cumSums[start-1][i] + prior);
        //        return 0;
        return out;
    }

    /**
       compute the gradient of the log-likelihood for a segment, under the MAP language model. 
       <b>not implemented</b>
       
       @param start the index of the first sentence in the segment
       @param end the index of the last sentence in the segment
       @param prior the symmetric dirichlet prior to use
    **/
    public double segMAPGradient(int start, int end, double prior){ return 0; }

    /**
       compute the gradient of the log-likelihood for a segment, under the DCM model
       
       @param start the index of the first sentence in the segment
       @param end the index of the last sentence in the segment
       @param logprior the log of the symmetric dirichlet prior to use
    **/
    public double segLLGradientExp(int start, int end, double logprior){
        double prior = Math.exp(logprior);
        //dl/d log(a) = (dl / da) * (da / d log(a)) = a dl / da 
        return prior * (m_dcm?segDCMGradient(start,end,prior):segMAPGradient(start,end,prior));
    }


    /**
       Just does a unit test on some stuff 
    **/
    public static void main(String argv[]){
        double[][] words = {{1,0,0,0,0},
                            {3,0,0,0,0},
                            {1,0,0,0,0},
                            {0,1.5,0,0,0},
                            {0,1,0,0,0},
                            {0,1,0,0,0},
                            {0,1,0,0,0},
                            {0,1,2.2,0,0}};
        int T = words.length;
        DPDocument dpd = new DPDocument(words,1,true);
        dpd.setGamma(new FastDoubleGamma());
        for (double a = 0.1; a < 1; a+= .1){
            double ll = dpd.segLLExp(1,T,Math.log(a));
            double grad = dpd.segLLGradientExp(1,T,Math.log(a));
            System.out.println(String.format("%.3f %.3f %.3f",a,ll,grad));
        }
    }

    double[][] cumSums;
    double[] cumSum;
    public boolean m_dcm;
    public boolean m_int_counts;
    FastDCM fastdcm;
    FastDigamma digamma;
}
