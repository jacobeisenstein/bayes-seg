package edu.mit.nlp.segmenter;

import ml.options.Options;
import ml.options.OptionSet;
import edu.mit.nlp.*;
import edu.mit.nlp.util.Utils;
import edu.mit.nlp.segmenter.dp.DPSeg;
import edu.mit.nlp.segmenter.mcmc.*;
import edu.mit.util.JacobUtil;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/**
 *  The purpose of this class is to provide a unified framework to evaluate and run
 *  various segmenters.  

 <h3>Evaluation</h3>
 To evaluate a segmenter on a dataset, here's what you say:
 
 <pre>
 SegTester -config config -dir dir -suff suff [-init init] [-debug]
      <b>config</b>   the configuration file for the experiment (see the config directory)
      <b>dir</b>      the directory where the data files are located
      <b>suff</b>     the suffix of the data files
      <b>init</b>     for initializable segmenters (e.g. {@link edu.mit.nlp.segmenter.mcmc.CuCoSeg}), 
                      this specifies the name of a file with the initial segmentations.
      <b>debug</b>    print debugging info                            
 
  Outputs: the configuration, the files that it's reading in, anything the segmenter itself wants to say, 
  and the pk/wd per file.
  </pre>

  <h3>Running</h3>
  
  To run a segmenter on some text, you say:

  <pre>

  cat file | SegTester -config config [-debug debug] [-num-segs num-segs]
      <b>config</b>   the configuration file for the experiment (see the config directory)
      <b>debug</b>    print debugging info
      <b>num-segs</b> number of segments desired.  if not provided, will be read from the file itself, unless
                      the configuration specifies that the number of segments is unknown
  </pre>
      
  Outputs: the configuration, the line numbers of the segment endpoints

  <h3>Proposed future functionality</h3>
  <ul>
  <li>print out hypothesized segmentations
  <li>statistical significance evaluations
  </ul>
  @author Jacob Eisenstein
 */

public class SegTester {
    public static void main(String[] args){
        Options options = new Options(args);
        options.addSet("eval",0);
        //        options.getSet("eval").addOption("foo",Options.Multiplicity.ZERO_OR_ONE);
        options.getSet("eval").addOption("config",Options.Separator.BLANK,Options.Multiplicity.ONCE);
        options.getSet("eval").addOption("dir",Options.Separator.BLANK,Options.Multiplicity.ONCE);
        options.getSet("eval").addOption("suff",Options.Separator.BLANK,Options.Multiplicity.ONCE);
        //-out is not yet supported, but it's supposed the write out the file with the segmentation markers
        options.getSet("eval").addOption("out",Options.Separator.BLANK,Options.Multiplicity.ZERO_OR_ONE);        
        options.getSet("eval").addOption("init",Options.Separator.BLANK,Options.Multiplicity.ZERO_OR_ONE);
        options.getSet("eval").addOption("debug",Options.Multiplicity.ZERO_OR_ONE);

        options.addSet("run",0);
        options.getSet("run").addOption("debug",Options.Multiplicity.ZERO_OR_ONE);
        options.getSet("run").addOption("num-segs",Options.Separator.BLANK,Options.Multiplicity.ZERO_OR_ONE);
        options.getSet("run").addOption("config",Options.Separator.BLANK,Options.Multiplicity.ZERO_OR_ONE);


        ml.options.OptionSet optset = options.getMatchingSet();
        if (optset == null) throw new IllegalArgumentException(usage_msg);
        
        try {
            SegTester seg_tester = new SegTester(optset);
            /* use reflection to create a segmenter */
            String segmenter_name = seg_tester.params.segmenter();
            Segmenter segmenter = null;
            segmenter = (Segmenter) Class.forName(segmenter_name).getConstructor(new Class[]{}).newInstance(new Object[]{});
            
            /* initialize and evaluate */
            boolean debug = optset.isSet("debug");
            segmenter.setDebug(debug);
            segmenter.initialize(optset.getOption("config").getResultValue(0));

            if (optset.getSetName().equals("eval")){
                System.out.println(seg_tester.params.getProps());
                seg_tester.loadFiles(optset);
                
                if (segmenter instanceof InitializableSegmenter &&
                    optset.isSet("init")){
                    ((InitializableSegmenter)segmenter).initSegs(optset.getOption("init").getResultValue(0));
                }
                seg_tester.eval(segmenter); //or maybe output results?
            } 
            //TODO: handle the pre-specified number of segments
            else { //it's run
                //get the file from stdin
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                List<String> lines = new ArrayList<String>();
                while (in.ready()){
                    lines.add(in.readLine());
                }

                if (lines.size() == 0){
                    throw new IllegalArgumentException("To run a segmenter, please provide some text on stdin.");
                }

                //this is not elegant, but we're just gonna write stuff out to a file. 
                //otherwise I'd have to really mess with Igor's TextWrapper or totally 
                //supercede it.
                File tmp = File.createTempFile("segmenter","tmp");
                PrintStream out = new PrintStream(new FileOutputStream(tmp));
                for (String line : lines){
                    out.println(line);
                }
                //create new mytextwrapper
                if (debug) System.out.println("loading text");
                MyTextWrapper text = new MyTextWrapper(tmp.getPath());
                SegTester.preprocessText(text, true, false, true, true, 0);
                if (debug) System.out.println("segmenting");
                int num_segs = optset.isSet("num-segs")?
                    (new Integer(optset.getOption("num-segs").getResultValue(0)).intValue()):
                    text.getReferenceSeg().size();
                List[] hyp_segs = segmenter.segmentTexts
                    (new MyTextWrapper[]{text},
                     new int[]{num_segs});
                System.out.println(hyp_segs[0]);

            }
        } catch (Exception e){
            e.printStackTrace();
            System.out.println(usage_msg);
        }
    }
    public SegTester(OptionSet optset) throws Exception {
        //deal with options
        String config_filename = optset.getOption("config").getResultValue(0);
        File config_file = new File(config_filename);
        Utils.ensure(config_file.canRead(), "File: "+config_filename+" is not accessible");
        FileInputStream fis = new FileInputStream(config_file);
        params = new SegTesterParams(config_file);
        loadStopWords();
    }

    protected void loadFiles(OptionSet optset){
        //load the files...
        final String the_suff = optset.getOption("suff").getResultValue(0).trim();
        File the_dir = new File(optset.getOption("dir").getResultValue(0).trim());
        FilenameFilter myfilt = new FilenameFilter(){
                public boolean accept(File dir, String name){
                    return name.endsWith(the_suff);
                }
            };
        String[] filenames = the_dir.list(myfilt);
        if (filenames == null) 
            throw new IllegalArgumentException("Cannot find any files in "+the_dir.getPath()+"*."+the_suff+"\n"+usage_msg);
        texts = new MyTextWrapper[filenames.length];
        //actually load them?
        for (int i = 0; i < filenames.length; i++){
            System.out.println(the_dir+"/"+filenames[i]);
            texts[i] = loadText(the_dir+"/"+filenames[i]);
        }
    }

    public MyTextWrapper loadText(String fileName) {
        MyTextWrapper slideWrapper = new MyTextWrapper(fileName);
        preprocessText(slideWrapper,
                       params.useChoiStyleBounds(),
                       params.isWindowingEnabled(),
                       params.isRemoveStopWords(),
                       params.isStemsEnabled(),
                       params.getWindowSize());
        return slideWrapper;
    }

    /**
       gets "paralinguistic" data, e.g. pause durations and prosodic markers.  not used
       in this implementation.
    **/
    public static ParaData getParaData(String filename){
        //parsefilename
        String newfilename = filename.substring(0,filename.lastIndexOf(".")) + para_ending;
        File parafile = new File(newfilename);
        ParaData output = null;
        if (parafile.exists()) try {output = new ParaData(parafile);} catch (Exception e){ e.printStackTrace(); }
        return output;
    }
    
    /**
       does some preprocessing stuff on the text --
       stemming, removing stop words, handling segment boundries, and
       breaking the text into K-word blocks.
       based on Malioutov's MinCutSeg.jar library

       @param text the text file to preprocess
       @param use_choi use choi-style segment boundaries
       @param is_windowing_enabled whether to break the text into fixed-length chunks (as opposed to using sentence breaks)
       @param window_size the size of the fixed-length chunks
       @param remove_stops whether to remove stopwords
       @param use_stems whether to use stemming

    **/
    public static void preprocessText(MyTextWrapper text, 
                                      boolean use_choi, 
                                      boolean is_windowing_enabled,
                                      boolean remove_stops,
                                      boolean use_stems,
                                      int window_size){
        if (use_choi) text.useChoiBreaks();
        text.storeRawText();
        if (is_windowing_enabled)
            text.parseWindows(window_size, use_stems);
        else
            text.parse(use_stems);

        if (remove_stops) {
            if (use_stems) text.removeStopWords(stemStopWords(stop_words));
            else text.removeStopWords(stop_words);
        } else {
        }
    } 
                                 
    /**
       if we're doing stemming, then we need to also stem the stopwords (otherwise they won't match)
       This does that.
    **/
    protected static List stemStopWords(List stopWords) {
        List stemmedList = new ArrayList();
        for (int i = 0; i < stopWords.size(); i++) {
            stemmedList.add(Utils.stemWord((String) stopWords.get(i)));
        }
        return stemmedList;
    }

    private void loadStopWords() {
        String stopWordFile = params.getStopWordsFile();
        try {
            stop_words.clear();
            BufferedReader br = new BufferedReader(new FileReader(stopWordFile));
            String line = br.readLine();
            while (line != null) {
                line = line.trim().toLowerCase();
                stop_words.add(line);
                line = br.readLine();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }
    
    /**
       Evaluate a segmenter.
       
       @param segmenter the segmenter class that we're evaluating

       Doesn't return anything, just prints stuff.  Uses Malioutov's evaluation code.
    */
    public void eval(Segmenter segmenter){
        //send all the texts in case the segmenter wants to do something jointly
        //as will be the case for CuCoSeg
        List[] hyp_segs = segmenter.segmentTexts(texts,MyTextWrapper.getNumSegs(texts));
        double total_pk_w=0; double total_wd_w=0; //word-level pk/wd
        for (int i = 0; i < hyp_segs.length; i++){
            List ref_seg = texts[i].getReferenceSeg();
            double pk_w = SegmentationScore.calcErrorProbablity(hyp_segs[i], ref_seg, texts[i],"Pk");
            double wd_w = SegmentationScore.calcErrorProbablity(hyp_segs[i], ref_seg, texts[i],"WD");
            int[] hyp_arr = new int[hyp_segs[i].size()]; int[] ref_arr = new int[ref_seg.size()];
            for (int j = 0; j < hyp_segs[i].size(); j++){ 
                hyp_arr[j] = (Integer) hyp_segs[i].get(j); 
            }
            for (int j = 0; j < ref_seg.size(); j++){ 
                ref_arr[j] = (Integer) ref_seg.get(j); 
            }

            System.out.println(String.format("%.4f %.4f", pk_w, wd_w));
            total_pk_w += pk_w; total_wd_w += wd_w;
        }
        total_pk_w /= hyp_segs.length; total_wd_w /= hyp_segs.length;
        System.out.println(String.format("%.4f %.4f",total_pk_w,total_wd_w));
    }
    
    private static String usage_msg = "Usage: SegTester -config config -dir dir -suff suff [-out out] -segmenter <bayes|mcs|ui> -options options";
    private static List stop_words = new ArrayList();
    private SegTesterParams params;
    protected MyTextWrapper[] texts;
    protected static String para_ending = ".xtra";
}
