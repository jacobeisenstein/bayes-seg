package edu.mit.nlp.segmenter.mcmc;

import java.io.*;
import java.util.*;
import edu.mit.util.JacobUtil;
import edu.mit.util.ling.CountsManager;
import edu.mit.util.stats.*;
import edu.mit.util.weka.LBFGSWrapper;
import edu.mit.nlp.*;
import edu.mit.nlp.segmenter.*;
import edu.mit.nlp.segmenter.dp.*;
import edu.mit.nlp.ling.LexMap;
import cern.jet.random.*;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

/**
   <h3>CuCoSeg -- Cue-phrase + Cohesion Segmentation</h3>

   Loads a second copy of all texts, from which stop words are not removed (because stopwords are sometimes good cues)
   Keeps a separate LexMap for each document.
   Thus, must also keep a separate fastdcm for each doc.
**/

public class CuCoSeg implements InitializableSegmenter {
    public CuCoSeg(){
        initializer = new BayesWrapper();
    }
    public void initialize(String config_filename){
        Properties props = new Properties();
        initializer.initialize(config_filename);
        try {
            props.load(new FileInputStream(config_filename));
            is_windowing_enabled = SegTesterParams.getBoolProp(props,"use-fixed-blocks",is_windowing_enabled);
            debug = SegTesterParams.getBoolProp(props,"debug",debug);
            dispersion = SegTesterParams.getDoubleProp(props,"segmentation-dispersion",dispersion);
            careful_debug = SegTesterParams.getBoolProp(props,"careful-debug",careful_debug);
            phi_b_0 = SegTesterParams.getDoubleProp(props,"phi-b-0",phi_b_0);
            theta_0 = SegTesterParams.getDoubleProp(props,"dirichlet-prior",theta_0);
            lambda_b = SegTesterParams.getIntProp(props,"lambda-b",lambda_b);
            k_num_moves = SegTesterParams.getIntProp(props,"num-mcmc-moves",k_num_moves);
            k_init_seed = SegTesterParams.getIntProp(props,"random-seed",-1);
            k_max_move = SegTesterParams.getIntProp(props,"max-move",k_max_move);
            k_param_period = SegTesterParams.getIntProp(props,"update-params-period",k_param_period);
            k_output_period = SegTesterParams.getIntProp(props,"output-period",k_output_period);
            k_burnin_duration = SegTesterParams.getDoubleProp(props,"burnin-duration",k_burnin_duration);
            k_max_burnin = SegTesterParams.getDoubleProp(props,"max-burnin-temp",k_max_burnin);
            k_cooling_duration = SegTesterParams.getDoubleProp(props,"cooling-duration",k_cooling_duration);
            use_stems = SegTesterParams.getBoolProp(props,"use-word-stems",use_stems);
            window_size = SegTesterParams.getIntProp(props,"window-size",window_size);
            use_duration = SegTesterParams.getBoolProp(props,"use-duration",use_duration);
            mcem_cuephrases = SegTesterParams.getBoolProp(props,"mcem-cuephrases",mcem_cuephrases);
            chi_squared_analysis = SegTesterParams.getBoolProp(props,"chi-squared-analysis",chi_squared_analysis);
            if (mcem_cuephrases){
                throw new Exception ("MCEM no longer supported");
            }
            k_mcem_update_period = SegTesterParams.getIntProp(props,"update-lm-period",k_mcem_update_period);
            cuephrase_file = props.getProperty("cuephrase-file",null);
            use_extra_features = SegTesterParams.getBoolProp(props,"use-extra-features",use_extra_features);
        } catch (Exception e){ e.printStackTrace(); }
        if (cuephrase_file != null){
            try {
                //read it into the cuephrases array
                BufferedReader in = new BufferedReader(new FileReader(cuephrase_file));
                b_cuephrases = new ArrayList<String>();
                boolean o = false;
                String dude;
                while (in.ready()){
                    dude = in.readLine();
                    if (dude.startsWith("=")) o = true;
                    if (!o)    b_cuephrases.add(dude);
                }
            } catch (Exception e){
                System.err.println("could not load cuephrases");
                e.printStackTrace();
            }
        }
    }
    public void setDebug (boolean debug) {
        this.debug = debug; 
        initializer.setDebug(debug);
    }

    /**
       initSegs -- load initial segmentation guesses from a file.  This is handy because
       if we're just trying different stuff with the MCMC part, we want to avoid the time-consuming
       initialization from the DP segmenter.
    */
    public void initSegs(String segfilename){
        try {
            BufferedReader in = new BufferedReader(new FileReader(segfilename));
            int ctr = 0;
            int numlines = 0;
            while (in.ready()){ in.readLine(); numlines++; }
            segs = new List[numlines]; //this should be -1 if there's an initialization at the end
            in.close();
            in = new BufferedReader(new FileReader(segfilename));
            while (in.ready()){
                String line = in.readLine();
                if (line.startsWith("[")){
                    //System.out.println(line);
                    String cursegs[] = line.substring(1,line.length()-1).split(", ");
                    //System.out.println(JacobUtil.formatArray(" ",cursegs));
                    segs[ctr] = new ArrayList(cursegs.length);
                    for (int j = 0; j < cursegs.length; j++) segs[ctr].add(new Integer(cursegs[j]));
                    ctr++;
                } else { //set parameters
                    String params[] = line.split(" ");
                    for (int i =0 ;i < params.length; i++){
                        String[] name_and_val = params[i].split("=");
                        if (name_and_val[0].startsWith("theta_0")) theta_0 = (new Double(name_and_val[1])).doubleValue();
                        if (name_and_val[0].startsWith("dispersion")) dispersion = (new Double(name_and_val[1])).doubleValue();
                    }
                    phi_b_0 = theta_0;
                    System.out.print(String.format("theta_0=%.3e ",theta_0));
                    if (use_duration) System.out.print(String.format("dispersion=%.3e",dispersion));
                    System.out.println();
                    read_params_from_init = true;
                }
            }
        } catch (Exception e){ e.printStackTrace(); }
    }

    /**
       massively long method that segments all the texts

       @param texts all the texts in the dataset
       @param K number of segments per document
       @return a list of arrays of segmentation points
    **/
    public List[] segmentTexts(MyTextWrapper[] texts, int[] K){
        this.texts = texts;
        this.K = K;
        D = texts.length;
        MyTextWrapper[] cue_texts = new MyTextWrapper[D];
        /* store the paralingual data */
        pause_by_sent = new double[D][];
        speaker_change_by_sent = new boolean[D][];
        
        /** obtain a universal lexmap across docs, load cue texts **/
        cue_lexmap = null;

        for (int i = 0; i < D; i++){ 
            cue_texts[i] = new MyTextWrapper(texts[i].getTextFilename());
            SegTester.preprocessText(cue_texts[i], true, is_windowing_enabled, false, use_stems, window_size);
            if (i == 0) cue_lexmap = cue_texts[0].getLexMap();
            else cue_lexmap.addLexMap(cue_texts[i].getLexMap());
            //load xtra if it's there
            if (use_extra_features){
                ParaData paradata = SegTester.getParaData(texts[i].getTextFilename());
                if (paradata != null){
                    pause_by_sent[i] = paradata.getPauses();
                    speaker_change_by_sent[i] = paradata.getSpeakerChange();
                    speaker_change_counts = new double[3][2];
                    pause_sums = new double[2]; pause_sums_sq = new double[2];
                    pause_dcm = new FastDCM(.001,2);
                } else {
                    use_extra_features = false;
                }
            }
        }
        cW = use_stems?cue_lexmap.getStemLexiconSize():cue_lexmap.getWordLexiconSize();
        
        T = new int[D]; //num sents per doc
        W = new int[D]; //vocab size per doc
        topic_words = new int[D][][]; //the per doc word occurence sparse matrix -- stopwords removed
        cue_words = new int[D][][]; //the per doc word occurence sparse matrix -- stopwords not removed

        //this is not actually enough...
        //i need to either keep the partial counts (cleaner code, slow to change the lambda values)
        //or alternatively, keep b/o/bo counts for every seg (ugly code, maybe slower in the main sampling part, quick to change lambda values)
        //probably best to keep the partial counts, but then it's gotta be doubles
        
        i_counts = new int[D][][]; //num docs x num segs x num words -- too big??
        b_counts = new int[cW]; //counts at beginning sentences
        if (chi_squared_analysis)
            non_b_counts = new int[cW]; //counts not at beginning sentences
        int total_samples_taken = 0;
        log_phi_b = new double[cW];
        pdurs = new double[D][];
        movedist_proposal = new double[k_max_move*2+1];  //McmcSeg.getMovedistProb(k_max_move, engine);
        for (int i = 1; i < k_max_move; i++){
            movedist_proposal[k_max_move + i] = Math.pow(i,-1.0/Math.sqrt(k_max_move));
            movedist_proposal[k_max_move - i] = Math.pow(i,-1.0/Math.sqrt(k_max_move)); //whatever            
        }
        cuephrase_proposal = new double[D][];
                
        for (int i = 0; i < D; i++) {
            //get the word occurrence table for the universal lexmap
            //this does capture stemmed versions of the words as well -- actually the cue phrase list has to be stems
            topic_words[i] = texts[i].createSparseWordOccurrenceTable();
            cue_words[i] = cue_texts[i].createSparseWordOccurrenceTable(cue_lexmap);

            W[i] = use_stems?texts[i].getLexMap().getStemLexiconSize():texts[i].getLexMap().getWordLexiconSize();
            i_counts[i] = new int[K[i]][W[i]];
            T[i] = topic_words[i].length;
            if (pause_by_sent[i] != null){
                assert(pause_by_sent[i].length == T[i] - 1);
            }
            pdurs[i] = new double[T[i]];
            
            cuephrase_proposal[i] = new double[T[i]];
            Arrays.fill(cuephrase_proposal[i],1);
            //go through the cue_lexmap
            if (b_cuephrases != null){
                double[][] b_woct = cue_texts[i].createWordOccurrenceTable(b_cuephrases);
                //start at 1 b/c 0 cannot be a seg point
                //                System.out.println(texts[i].getTextFilename()+" "+b_woct[0].length);
                if (b_woct.length > 0){
                    for (int j = 0; j < b_woct.length; j++) {
                        for (int t = 1; t < cuephrase_proposal[i].length; t++)
                            cuephrase_proposal[i][t] += b_woct[j][t];
                    }
                }
            }
        }

        if (segs == null){
            //initialize the segmentation using DPSeg
            if (debug) System.out.print ("initializing from DP... ");
            segs = initializer.segmentTexts(texts, K);
            if (is_windowing_enabled) {
                for (int i = 0; i < D; i++){
                    segs[i] = MinCutSeg.convertSentence2WindowSegmentation(segs[i], texts[i]);
                }
            }
        } else if (debug) System.out.print("loading prewritten initialization... ");
        if (debug){
            System.out.println("INITIAL SEGMENTATION: ");
            for (int i = 0; i < D; i++){ System.out.println(segs[i]); }
        }

        if (! read_params_from_init && initializer.em_params){
            //set my params to those guys
            double[] params = initializer.getParams();
            theta_0 = Math.exp(params[0]);
            phi_b_0 = theta_0;
            System.out.print(String.format("theta_0=%.3e ",theta_0));
            if (use_duration) {
                dispersion = Math.exp(params[params.length-1]);
                System.out.print(String.format("dispersion=%.3e",dispersion));
            }
            System.out.println();
        }
    
        if (debug) System.out.println ("done");
        System.out.println(K);
        
        updateCounts(lambda_b);
        setPDurs();

        if (k_init_seed > 0){
            System.out.println("initializing engine with: "+k_init_seed);
            engine = new MersenneTwister(k_init_seed);
        }
        else {
            Date thedate = new Date();
            System.out.println("Initializing engine with: "+thedate);
            engine = new MersenneTwister(thedate);
        }
        Uniform.staticSetRandomEngine(engine);
        lambda_prob = new Beta(2,2,engine);
        dirichlet_prob = new Gamma(1,1,engine);
        annealer = new Annealer(k_burnin_duration, k_cooling_duration, k_max_burnin, k_num_moves);

        b_fastgamma = new FastIntGamma(cW,phi_b_0);
        i_fastgamma = new FastIntGamma(cW,theta_0);

        digamma = new FastDigamma();
        dcm_b = new FastDCM(phi_b_0,cW, b_fastgamma);
        dcm = new FastDCM[D];
        for (int i = 0; i < D; i++) dcm[i] = new FastDCM(theta_0,W[i],i_fastgamma);


        //these guys are for randomization
        ArrayList<Integer> docids = new ArrayList<Integer>(); for (int d = 0; d < D; d++) docids.add(d);

        printStatus(System.out,-1); //print initial status
        //run a bunch of sampling iterations
        for (int i = 0; i < k_num_moves; i++){
            //do a move in each document
            Collections.shuffle(docids);
            for (int docid = 0; docid < D; docid++){
                // select a boundary, then a move
                int d = ((Integer)docids.get(docid)).intValue();
                if (K[d] == 1) continue; //if there's only one segment, don't bother
                int seg = Uniform.staticNextIntFromTo(0,(int)K[d]-2); //cannot select last segpt, it's the end of doc 
                //int amount = (int)((k_max_move * 2 + 1) * movedist_proposal.nextDouble() - k_max_move);
                Empirical proposal = getMoveProposal(d,seg);
                double raw_move = proposal.nextDouble();
                int amount = (int)((k_max_move*2+1)*raw_move-k_max_move);
                ///// this is due to some weirdness with colt.jet.random.Empirical.pdf()
                ///// it returns negative probabilities and you have to index starting at 1, not zero
                if (amount == 0) continue; //nothing to do here
                double trans_prob = -proposal.pdf(1+amount+k_max_move);
                if (careful_debug) {
                    System.out.print (String.format("Considering seg %d, %d (%d): ",seg,amount,amount+k_max_move));
                    System.out.println("proposal prob: "+trans_prob);
                }
                //add counts, subtract counts
                try {                    
                    assert (validMove(segs[d],seg,amount));
                    double cur_log_prob = computeLogProb(d,seg);
                    int old_seg_point = ((Integer)segs[d].get(seg)).intValue();
                    //double cur_log_prob = computeLogProb();
                    updateSegmentation(d,seg,amount);
                    proposal = getMoveProposal(d,seg);
                    //see above note about Empirical.pdf()
                    double inv_trans_prob = -proposal.pdf(1-amount+k_max_move);
                    double p_accept = inv_trans_prob / trans_prob;
                    if (careful_debug) System.out.println("inverse proposal prob: "+inv_trans_prob);
                    double new_log_prob = computeLogProb(d,seg);
                    //double new_log_prob = computeLogProb();
                    if (careful_debug) System.out.println("likelihood prob: "+Math.exp(new_log_prob-cur_log_prob));
                    p_accept *= Math.exp(new_log_prob - cur_log_prob); //likelihood ratio
                    if (careful_debug) System.out.print(String.format("%.1f %.1f %.3f ",new_log_prob,cur_log_prob,p_accept));
                    //                    if (careful_debug) System.out.print(String.format("%.1f %.1f",new_global_log_prob,cur_global_log_prob));
                    
                    //compute new probability of move
                    //p_accept = (new_log_prob > cur_log_prob)?1:0;
                    p_accept = annealer.annealWithoutUpdate(p_accept);
                    if (p_accept > 0 && Uniform.staticNextDouble() < p_accept){
                        seg_moves_accepted++;
                        if (careful_debug) System.out.println(" accepted");
                    } else {
                        updateSegmentation(d,seg,-amount);
                        if (careful_debug) System.out.println(" rejected");
                    }
                } catch (Exception e){
                    System.out.println(String.format("Problem with file %s, seg=%d, amount = %d",texts[d].getTextFilename(),seg,amount));
                    e.printStackTrace();
                }
            }
            //update the parametrs
            if (k_param_period > 0 && i % k_param_period == 0){ 
                //mcem -- interleave gradient descent in a markov chain
//                 if (debug)
//                 System.out.println(String.format("Entering optimizer with: %.2e %.2e %.2e %f",
//                                                  theta_0, phi_b_0, phi_o_0, computeLogProb()));
                PriorOptimizer opt = new PriorOptimizer();
                
                opt.m_eps = 1e-4; opt.m_num_corrections = 3; //can afford this because it's relatively easy optimization
                double[] estimate = new double[use_duration?2:1];
                estimate[0] = Math.log(theta_0);
                if (use_duration) estimate[1] = Math.log(dispersion);

                opt.setEstimate(estimate);
                //                opt.scanParams(new double[]{Math.log(theta_0),Math.log(dispersion)});

                opt.setMaxIteration(20);
                opt.setDebug(debug); //this is just too much info
                try { opt.findArgmin(); }
                catch (Exception e){}// e.printStackTrace(); }
                theta_0 = Math.exp(opt.getVarbValues()[0]); 
                if (use_duration) dispersion = Math.exp(opt.getVarbValues()[1]);
                
                //set the cue phrase priors using Minka's approximation phi_0 = t / n^2, where t is number of singletons
                phi_b_0 = minkaApprox(b_counts);
                
                for (int d = 0;d < D; d++) setDCMPrior(dcm[d],theta_0);
                try {
                    setDCMPrior(dcm_b,phi_b_0);
                } catch (java.lang.ArithmeticException e){
                    System.err.println("problem with phi_b_0="+phi_b_0);
                    e.printStackTrace();
                }
                setPDurs();
            }

            //print out the status
            if (i % k_output_period == 0) printStatus(System.out,i);
            annealer.update();
        }
        if (is_windowing_enabled){
            for (int d = 0; d < D; d++) {
                segs[d] = MinCutSeg.convertWindow2SentenceSegmentation(segs[d],texts[d]);
            }
        }
        return segs;
    }

    /************************ status message stuff *******************/ 

    /**
       prints a status message.  the format is
       <pre>
       iteration LL [A1 A2 A3] [theta0 phi_b0 dispersion] Pk WD
       
         A1 = num moves accepted since last message
         A2 = proportion of moves accepted since last message
         A3 = f(.5), where f() is the annealing function
         theta0 = symmetric dirichlet prior on language models
         phi_b0 = symmetric dirichlet prior on cue phrases
         dispersion = dispersion parameter on segment durations (not used) 
         Pk = metric of segmentation quality
         WD = other metric of segmentation quality
         </pre>
         
         @param out the printstream to write the message to
         @param i the iteration number
    **/
    protected void printStatus(PrintStream out, int i){
        double total_pk =0; double total_wd = 0;
        for (int d = 0; d < D; d++){
            List curseg = null;
            if (is_windowing_enabled) curseg = MinCutSeg.convertWindow2SentenceSegmentation(segs[d],texts[d]);
            else curseg = segs[d];
            total_pk += SegmentationScore.calcErrorProbablity(curseg,texts[d].getReferenceSeg(),texts[d],"Pk");
            total_wd += SegmentationScore.calcErrorProbablity(curseg,texts[d].getReferenceSeg(),texts[d],"WD");
        }
        out.println(String.format
                           ("%d %.3f [%d %.2e %.2f] [%.1e %.1e %.1e] %.4f %.4f",
                            i,
                            computeLogProb(),
                            seg_moves_accepted,
                            (double)seg_moves_accepted/(D*k_output_period),
                            annealer.getHalfProbAnnealed(),
                            theta_0, phi_b_0, dispersion,
                            total_pk / D,
                            total_wd / D));
        if (use_extra_features){
            out.println(" O="+speaker_change_counts[0][0]+"/"+speaker_change_counts[0][1]+
                        " B="+speaker_change_counts[1][0]+"/"+speaker_change_counts[1][1]+
                        " I="+speaker_change_counts[2][0]+"/"+speaker_change_counts[2][1]);
        }
        seg_moves_accepted = 0;
        param_moves_accepted = 0;
//         out.println("B: "+JacobUtil.formatArray("%.1f"," ",b_counts));
//         for (int d = 0; d < D; d++){
//             for (int j = 0; j < i_counts[d].length; j++){
//                 out.println("I"+j+": "+JacobUtil.formatArray("%.1f"," ",i_counts[d][j]));
//             }
//         }
        
//         System.out.println("SEG SUMS");
//         for (int d = 0; d < D; d++){
//             double doc_sum = 0;
//             for (int j = 0;j < i_counts[d].length; j++){
//                 double seg_sum = 0;
//                 for (int k = 0; k < i_counts[d][j].length; k++) seg_sum+=i_counts[d][j][k];
//                 System.out.println(seg_sum);
//                 doc_sum+=seg_sum;
//             }
//             System.out.println(doc_sum);
//             System.out.println();
//         }
        
        if (debug && chi_squared_analysis){
            Unigram[] topn = getSortedUnigrams(cue_lexmap,b_counts,non_b_counts);
            out.print("B: ");
            for (int j = 0; j < 50; j++){
                out.println(String.format("%s %.2f %.0f %.0f",
                                        use_stems?topn[j].stem:topn[j].word,
                                        topn[j].chi_squared,
                                        topn[j].b_count,
                                        topn[j].non_b_count));
                
            }
            out.println();
        }
    }

    /**
       This class was to compute the hypothesized cue phrases
    **/
    class Unigram implements Comparable {
        String word;
        String stem;
        double b_count;
        double non_b_count;
        double chi_squared;
        double computeChiSquared(int total_b_count, int total_non_b_count){
            double total_count = b_count + non_b_count;
            double expected_b_count = ((double) total_b_count / (total_b_count + total_non_b_count)) * total_count;
            double expected_non_b_count = total_count - expected_b_count;
            if (total_count == 0) chi_squared = 0;
            else {
                chi_squared = 
                    (b_count - expected_b_count) * 
                    (b_count - expected_b_count) / expected_b_count + 
                    (non_b_count - expected_non_b_count) * 
                    (non_b_count - expected_non_b_count) / expected_non_b_count;
                if (b_count < expected_b_count) chi_squared *= -1;
            }
            return chi_squared;
        }
        public int compareTo(Object o){
            if (o instanceof Unigram){
                return (new Double(((Unigram)o).chi_squared)).compareTo(new Double(chi_squared));
            }
            //                return (new Double(count)).compareTo(new Double(((Unigram)o).count));
            return 0;
        }
    }

    public Unigram[] getSortedUnigrams(LexMap lexmap, int[] b_counts, int[] non_b_counts){
        Unigram[] unigrams = new Unigram[b_counts.length];
        int total_b_counts = 0; int total_non_b_counts = 0;
        for (int i = 0; i < b_counts.length; i++){
            total_b_counts += b_counts[i]; total_non_b_counts += non_b_counts[i];
        }
        for (int i = 0; i < b_counts.length; i++){
            unigrams[i] = new Unigram();
            unigrams[i].word = lexmap.getWord(i);
            unigrams[i].stem = lexmap.getStem(i);
            unigrams[i].b_count = b_counts[i];
            unigrams[i].non_b_count = non_b_counts[i];
            unigrams[i].computeChiSquared(total_b_counts - b_counts[i], total_non_b_counts - non_b_counts[i]);
        }
        Arrays.sort(unigrams);
        return unigrams;
    }


    /********************** param updating stuff ***********************/
    
    /**
       Since durations are discrete, we keep a cache of the probability of each 
       duration length.  This fills the cache, given our parameters.
    **/
    public void setPDurs(){
        if (Math.log(dispersion)>10) dispersion = Math.exp(10);
        for (int d = 0; d < D; d++){
            double edur = (double) pdurs[d].length / K[d];
            for (int t = 0; t < pdurs[d].length; t++){
                pdurs[d][t] = Stats.myLogNBinPdf2(t,edur,dispersion);
            }
        }
    }
    
    
    /**
       Set the symmetric prior on the DCM language models

       @param dcm the DCM cache
       @param prior the new prior
    **/
    public void setDCMPrior(FastDCM dcm, double prior){
        dcm.setPrior(prior);
    }

    /***************** likelihood and gradient stuff ************************************/

    /**
       sets the prior on the cue phrase language model, using
       the approximation proposed by Minka in "Estimating a Dirichlet Distribution" (eq 114)
    **/
    protected double minkaApprox(int[] counts){
        double sum = 0; double num_singletons = 0;
        for (int i = 0; i < counts.length; i++){
            if (counts[i] == 1) num_singletons++;
            sum+= counts[i];
        }
        if (sum == 0) return 1;
        if (num_singletons > 1) num_singletons=1;
        return num_singletons / ((double)sum * sum);
    }

    /** An LBFGS optimizer to search the parameter space **/
    protected class PriorOptimizer extends LBFGSWrapper {
        public PriorOptimizer(){ 
            super(use_duration?2:1); // one parameters to learn -- we're not doing b and o this way anymore
            //compute all the sums that you'll need
            for (int i = 0; i < b_counts.length; i++) b_sum += b_counts[i];
            seg_sums = new double[D][];
            for (int d = 0; d < D; d++){
                seg_sums[d] = new double[i_counts[d].length];
                for (int j = 0; j < i_counts[d].length; j++){
                    for (int i = 0; i < i_counts[d][j].length; i++){
                        seg_sums[d][j] += i_counts[d][j][i];
                    }
                }
            }
        }
        
        /* for debugging the gradient computation */
        public void scanParams(double[] params){
            for (int i = 0; i < params.length; i++){
                double orig_val = params[i];
                System.out.println("Scanning parameter "+i);
                for (double j = orig_val-5; j <= orig_val+5; j += .2){
                    params[i] = j;                        
                    double x = objectiveFunction(params);
                    double dx[] = evaluateGradient(params);
                    System.out.println(String.format("%.2f %.3f %.3f %.3f",params[i],Math.exp(params[i]),x,dx[i]));
//                     System.out.println(" B prior: " + dcm_b.getPrior());
                }
                params[i] = orig_val;
            }
        }

        /** assumes params are in log form */
        public double objectiveFunction(double[] params){
            for (int i = 0; i < D; i++) {
                setDCMPrior(dcm[i], Math.exp(params[0]));
            }
            if (use_duration) dispersion = Math.exp(params[1]);
            setPDurs();
            return -computeLogProb();
        }

        /** assumes params are in log form **/ 
        public double[] evaluateGradient(double[] params){
            double[] output = new double[use_duration?2:1];
            for (int d = 0; d < D; d++){
                for (int j = 0; j < i_counts[d].length; j++){
                    //we're returning dl / d\log \theta = (dl / d\theta) (d\theta / d\log\theta)
                    output[0] -= Math.exp(params[0]) * 
                        computeGradientForSegment(i_counts[d][j], seg_sums[d][j], Math.exp(params[0]), digamma);
                }
            }
            
            if (use_duration) {
                for (int d = 0; d < D; d++){
                    double edur = (double)T[d]/(double)K[d];
                    output[1] -= Stats.computeDispersionGradient
                        (((Integer)segs[d].get(0)).intValue(), edur, params[params.length-1], digamma);
                    for (int j =1;j < K[d]; j++){
                        output[1] -= Stats.computeDispersionGradient
                            (((Integer)segs[d].get(j)).intValue() - ((Integer)segs[d].get(j-1)).intValue(),
                             edur, params[params.length-1], digamma);
                    }
                }
            }


            return output;
        }   

        public double computeGradientForSegment(int[] counts, double sum, double prior, FastDigamma digamma){
            if (prior == 0) return Double.MAX_VALUE;
            int W = counts.length;
            //I wish I could find the math justifying this.  But it seems to work in DPDocument
            double out = W * ( digamma.digamma (W * prior) - digamma.digamma(sum + W*prior) - digamma.digamma(prior));
            for (int i = 0; i < W; i++) out += digamma.digamma(counts[i]+prior);
            

            return out;
            
        }
        
        double[][] seg_sums;
        double b_sum=0;
    }

    /* compute the likelihood of the extralinguistic (?) indicators, pause and speaker change 
       I think they might have been called "paralinguistic" elsewhere..
    **/
    public double computeXtraProb(){
        double output = 0;
        for (int i = 0; i < speaker_change_counts.length; i++){
            output += pause_dcm.logDCM(speaker_change_counts[i]);
        }
        //deal with pause probabilities
        return output;
    }

    /**
       computes the overall log probability
    **/
    public double computeLogProb(){
        double output = computeCueLogProb();
        if (use_extra_features) output += computeXtraProb();
//         System.out.print(String.format(" computing log prob %.2f %.2f\n cue log prob = %.1f",
//                                          dcm_b.getPrior(),
//                                          dcm[0].getPrior(),
//                                          output));
        for (int d = 0; d < D; d++){
            for (int i = 0; i < K[d]; i++){
                output += dcm[d].logDCM(i_counts[d][i]);
                //duration stuff
                if (use_duration){
                    int seg_start = 0;
                    if (i > 0) seg_start = ((Integer)segs[d].get(i-1)).intValue()-1;
                    output += pdurs[d][((Integer)segs[d].get(i)).intValue()-seg_start-1];
                }
            }
        }

        return output;
    }
    

    /**
       computes the portion of the log-probability associated
       with a change to segment seg in doc
       considers the b-counts, o-counts, and the i-counts 
       for seg, seg-1, and seg+1 (where applicable)
    **/
    public double computeLogProb(int doc, int seg){
        double output = computeCueLogProb();
        if (use_extra_features) output += computeXtraProb();
        int seg_start =0;
        if (seg > 0) {
            output += dcm[doc].logDCM(i_counts[doc][seg-1]);
            //deal with duration
            if (use_duration){
                seg_start = (seg - 2 >= 0)?((Integer)segs[doc].get(seg-2)).intValue():0;
                output += pdurs[doc][((Integer)segs[doc].get(seg-1)).intValue()-seg_start];
            }
        }
        output += dcm[doc].logDCM(i_counts[doc][seg]);
        //deal with duration
        if (use_duration){
            seg_start = (seg - 1 >= 0)?((Integer)segs[doc].get(seg-1)).intValue():0;
            output += pdurs[doc][((Integer)segs[doc].get(seg)).intValue()-seg_start];
        }
        if (seg + 1 < K[doc]) {
            output += dcm[doc].logDCM(i_counts[doc][seg+1]);
            //deal with duration
            if (use_duration){
                output += pdurs[doc][((Integer)segs[doc].get(seg+1)).intValue()-((Integer)segs[doc].get(seg)).intValue()];
            }
        }
        return output;
    }

    /**
       computeCueLogProb()
       computes the log-likelihood of the cue phrase counts
    **/
    protected double computeCueLogProb(){
        double output = 0;
        output += dcm_b.logDCM(b_counts);
        return output;
    }

    /*************************************** move proposal stuff ***************************/

    /**
       assesses whether a given move is valid (doesn't cross segment boundaries)
    **/
    public static boolean validMove(List segpoints, int seg, int amount){
        int new_segpt = amount + ((Integer)segpoints.get(seg)).intValue();
        //should not be geq the next segpoint (last one is the end of the doc)
        int upper_limit = ((Integer)segpoints.get(seg+1)).intValue();
        int lower_limit = seg>0?((Integer)segpoints.get(seg-1)).intValue():0;
        
//         if (debug) System.out.print(String.format(" evaluating %d->%d %d %d ",
//                                          ((Integer)segpoints.get(seg)).intValue(),
//                                          new_segpt,upper_limit,lower_limit));
        return (new_segpt < upper_limit && new_segpt > lower_limit);
    }
    
    /**
       generates an empirical distribution over moves of a given segmentation point
    **/
    public Empirical getMoveProposal(int doc, int seg){
        int segpt = ((Integer)segs[doc].get(seg)).intValue();
        double[] proposal = new double[movedist_proposal.length];
        assert (proposal.length == k_max_move * 2 + 1);
        int prevsegpt = 0; if (seg>0) prevsegpt = ((Integer)segs[doc].get(seg-1)).intValue();
        int nextsegpt = ((Integer)segs[doc].get(seg+1)).intValue();
//         System.out.println("PRINTING MOVE PROPOSAL "+segpt+" "+prevsegpt+" "+nextsegpt);
        for (int i = 0; i < proposal.length; i++) {
            int t = segpt + i - k_max_move;
            if (t > prevsegpt && t < nextsegpt) { //ensure validity{
                try {
                    proposal[i] = movedist_proposal[i] * cuephrase_proposal[doc][segpt+i-k_max_move]; 
                } catch (Exception e){
                    System.err.println(segpt+" "+i+" "+k_max_move+" "+prevsegpt+" "+nextsegpt+" "+cuephrase_proposal[doc].length);
                    e.printStackTrace();
                    System.exit(0);
                }
                //   System.out.println(" "+movedist_proposal[i]+" "+cuephrase_proposal[doc][segpt+i-k_max_move]);
            }
            //            System.out.println(i+" "+proposal[i]);
        }
        proposal[k_max_move] = .00001; //just in case there's no probability mass anywhere else
        //for (int i = 0;i < proposal.length; i++){ proposal[i]/=sum;}
//         System.out.println(JacobUtil.formatArray("%.2f"," ",proposal));
        //proposal is not normalized.  we are assured that this is not necessary
        Empirical out = new Empirical(proposal,Empirical.NO_INTERPOLATION,engine);
//         for (int i = 0; i < proposal.length; i++){
//             System.out.print(String.format("%.2f ",out.pdf(i+1)));
//         }
//         System.out.println();
//         int outcomes[] = new int[proposal.length];
//         for (int i = 0; i < 10000; i++){
//             outcomes[(int)(out.nextDouble() * proposal.length)]++;
//         }
//         System.out.println(JacobUtil.formatArray("%d"," ",outcomes));
        return out;
    } 

    /************************************ count updating stuff **********************/
    
    /** update the segmentation
        move the segpt in the doc by the amount
        will update segs[]
        and also all the counts
    **/
    public void updateSegmentation(int doc, int seg, int amount){
        double old_doc_sum = computeDocSum(doc);
        int old_segpt = ((Integer) segs[doc].get(seg)).intValue();
        int new_segpt = ((Integer)segs[doc].get(seg)).intValue()+amount;
        
        //        System.out.println("updating segmentation: "+old_segpt+" "+new_segpt);

        int t1 = old_segpt<new_segpt?old_segpt:new_segpt;
        if (t1 > 0) t1--; //consider point before segment
        int t2 = old_segpt<new_segpt?new_segpt:old_segpt;
        if (t2 < T[doc]) t2++; //consider point after

        //System.out.println("updating segmentation: "+seg+" "+amount+" "+t1+" "+t2);
        //System.out.println(segs[doc]);

        for (int i =t1; i < t2; i++) subCountsForSentence(doc, i);
        segs[doc].set(seg,new_segpt);
        for (int i=t1; i < t2; i++) addCountsForSentence(doc, i);
        //this won't be true because of the topic_words versus cue_words (stopwords)
        //assert(computeDocSum(doc)==old_doc_sum);
        //System.out.println(segs[doc]);
    }

    private double computeDocSum(int doc){
        double doc_sum = 0;
        for (int j = 0;j < i_counts[doc].length; j++){
            double seg_sum = 0;
            for (int k = 0; k < i_counts[doc][j].length; k++) seg_sum+=i_counts[doc][j][k];
            doc_sum+=seg_sum;
        }
        return doc_sum;
    }

    /**
       addCountsForSentence
       
       i -- the document
       j -- the sentenec

       uses the segs[] variable:

       complexity: K[i] + N[i][j], where K is the number of segs, and N[i][j] is the number of words in sent j
    **/
    public void addCountsForSentence(int doc, int t){
        changeCountsForSentence(doc,t,1);
    }

    public void subCountsForSentence(int doc, int t){
        changeCountsForSentence(doc,t,-1);
    }

    protected void changeCountsForSentence(int doc, int t,int sign){
        boolean beginning = false;
        boolean ending = false;
        boolean afterbeginning = false;
        int seg = -1;
        if (t == 0) beginning = true; 
        //        System.out.println("changing counts for sentence: "+doc+" "+K[doc]+" "+t+" "+sign);
        for (int j = 0; j < K[doc]; j++){
            int segstart = ((Integer)segs[doc].get(j)).intValue();
            //  System.out.println(String.format(" segment %d starts at %d",j,segstart));
            if (segstart==t-1) {afterbeginning = true; }
            if (segstart==t) {beginning = true; }
            if (segstart==t+1) { ending = true; seg = j; break; }
            if (segstart>t+1) { seg = j; break; }
        }
        if (beginning){
            CountsManager.addToCountsFirst(b_counts,cue_words[doc][t], lambda_b * sign);
//             if (chi_squared_analysis)
//                 CountsManager.addToCountsRest(non_b_counts,cue_words[doc][t], lambda_b * sign);
            CountsManager.addToCountsFirst(i_counts[doc][seg],topic_words[doc][t], (1-lambda_b) * sign);
            CountsManager.addToCountsRest(i_counts[doc][seg],topic_words[doc][t],sign);

            if (use_extra_features){
                if (t > 0) //can't do it for the first sentence
                    speaker_change_counts[0][speaker_change_by_sent[doc][t-1]?0:1]+=sign; 
            }
        } else {
            CountsManager.addToCounts(i_counts[doc][seg],topic_words[doc][t],sign);
            if (chi_squared_analysis)
                CountsManager.addToCountsFirst(non_b_counts,cue_words[doc][t],sign);
            if (use_extra_features){
                if (afterbeginning){ //if it's the one right after the beginning
                    assert(t>0);
                    speaker_change_counts[1][speaker_change_by_sent[doc][t-1]?0:1]+=sign; 
                }
                else
                    speaker_change_counts[2][speaker_change_by_sent[doc][t-1]?0:1]+=sign; 
            }
        }   
    }

        
    /** update the counts given a new lambda parameter **/
    public void updateCounts(int lambda_b){
        this.lambda_b = lambda_b; 
        //System.out.println("updating counts "+lambda_b+" "+lambda_o);
        //setup the counts appropriately
        Arrays.fill(b_counts,0);
        if (speaker_change_counts != null){
            Arrays.fill(pause_sums,0);
            Arrays.fill(pause_sums_sq,0);
            for (int i = 0; i < 3; i++){
                Arrays.fill(speaker_change_counts[i],0);
            }
        }
        for (int i = 0; i < D; i++){
            for (int j = 0; j < K[i]; j++){
                Arrays.fill(i_counts[i][j],0);
            }
        }
        for (int i = 0; i < D; i++){
            for (int t = 0; t < T[i]; t++){
                addCountsForSentence(i,t);
            }
        }
        if (careful_debug){
            System.out.println("B: "+JacobUtil.formatArray("%.0f"," ",b_counts));
            for (int i = 0; i < D; i++){
                for (int j = 0; j < i_counts[i].length; j++){
                    System.out.println("I["+i+","+j+"]: "+JacobUtil.formatArray("%.0f"," ",i_counts[i][j]));
                }
            }
        }
    }

    boolean debug = false;
    boolean careful_debug = false;
    double phi_b_0 = .01; FastDCM dcm_b;
    double theta_0 = .01; FastDCM[] dcm;
    double dispersion = 10;
    int lambda_b=1;
    BayesWrapper initializer;

    //the reason for the distinction is that stopwords are not removed from cue words
    int[][][] topic_words; //sparse matrix of words (docs x sentences x bag-of-words)
    int[][][] cue_words; //sparse matrix of words (docs x sentences x bag-of-words)
    int[][][] i_counts; //within-segment counts
    int[] b_counts; //segment-beginning counts -- in MCEM these are summed across segments
    int[] non_b_counts; //counts for non-sentence-beginning words.  this is different from
    //the sum of i_counts, because stopwords are included
    MyTextWrapper[] texts;
    LexMap cue_lexmap, topic_lexmap;
    List[] segs = null;
    double[][] pdurs;
    //mcem stuff
    double[] log_phi_b;

    //xtra stuff
    double[][] pause_by_sent; //D x (T[d] - 1)
    boolean[][] speaker_change_by_sent; //D x (T[d] - 1)
    double[][] speaker_change_counts=null; //3 x 2 (break,post-break,other, change vs no-change)
    double[] pause_sums = null; //break vs. non-break
    double[] pause_sums_sq = null; //break vs. non-break
    double pause_mean_prior_mean; double pause_mean_prior_count; //eh i hate gaussians let's just do speaker-change first
    FastDCM pause_dcm;
    boolean use_extra_features;

    int[] K; //num-segs-per-document
    int D; //num documents
    int[] W; //vocabulary size per document
    int cW; //cue word vocabulary size
    int[] T; //num sentences per document

    //get all the static params below from the config file
    static int k_num_moves = 10000; 
    static int k_init_seed = 1000; 
    static int k_max_move = 5;
    static int k_param_period = 10;
    static int k_mcem_update_period = 10;
    static double k_burnin_duration = .1;
    static double k_cooling_duration = .1;
    static double k_max_burnin = 1;
    static int k_output_period = 100;
    String cuephrase_file = null;
    int seg_moves_accepted = 0;
    int param_moves_accepted = 0;
    static boolean use_stems = true;
    int window_size = 25;
    boolean use_duration = false;
    boolean is_windowing_enabled = false;
    boolean mcem_cuephrases = true;
    boolean read_params_from_init = false;
    boolean chi_squared_analysis = false; //do a chi-squared analysis on cue words
   
    RandomEngine engine;
    double[] movedist_proposal; //multinomial distribution over moves, given move size
    double[][] cuephrase_proposal; //distribution over moves, given number of cue phrase words
    List<String> b_cuephrases = null;
    Beta lambda_prob; //beta distribution over lambda parameters
    Gamma dirichlet_prob; //gamma distribution over dirichlet priors
    FastIntGamma b_fastgamma; //caching gamma function computer
    FastIntGamma i_fastgamma; //caching gamma function computer
    FastDigamma digamma; //caching digamma function computer
    Annealer annealer;
}
