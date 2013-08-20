package edu.mit.nlp.segmenter.wrappers;

import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import edu.mit.nlp.segmenter.*;
import edu.mit.nlp.MyTextWrapper;
import java.io.*;

/** wraps the LCSeg segmenter **/
public class LCSegWrapper implements Segmenter {
    public LCSegWrapper(){ 
    }
    //currently, there are no parameters
    public void initialize(String config_filename){
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(config_filename));
            num_segs_known = SegTesterParams.getBoolProp(props,"num-segs-known",true);
            is_windowing_enabled = SegTesterParams.getBoolProp(props,"use-fixed-blocks",false);
	    lcseg_path = props.getProperty("lcseg-path","segment");
	    
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public List[] segmentTexts(MyTextWrapper[] texts, int[] num_segs){
        Runtime runtime = Runtime.getRuntime();
        List[] hyps = new List[texts.length];
        for (int i = 0; i < texts.length; i++){
            try {
                String segline = lcseg_path+" -a .5 -w 2 -h 11 ";
                //String segline = "/afs/csail/u/j/jacobe/motifs/baselines/LCSeg/lcseg/bin/segment ";
                if (num_segs_known) segline = segline+ "-n "+num_segs[i]; 
                segline = segline + " "+texts[i].getTextFilename();
                System.out.println(segline);
                 Process proc = runtime.exec(segline); //it doesn't seem to be getting the arguments
//                 PrintStream out = new PrintStream(proc.getOutputStream());
//                 for (int j = 0; j < texts[i].getWindowCount(); j++){
//                     out.println(texts[i].getWindowString(j));
//                 }
//                 out.close();
                BufferedReader bufferedreader =
                    new BufferedReader(new InputStreamReader(proc.getInputStream()));
                int segctr = 0; int linectr = 0; String line;
                hyps[i] = new ArrayList();
                while ((line = bufferedreader.readLine())!=null){
                    //                    System.out.println("OUT: "+line);
                    if (line.startsWith("====")){
                        if (segctr++ > 0) {//don't add the first one
                            hyps[i].add(linectr);
                        }
                    } else{
                        linectr++;
                        //                        if (debug) System.out.print((linectr)+": ");
                    }
                    //                    if (debug) System.out.println(line);
                }
                if (debug) System.out.println("hyp pre: "+hyps[i]);
                if (is_windowing_enabled) hyps[i] = MinCutSeg.convertWindow2SentenceSegmentation(hyps[i], texts[i]);
                if (debug) System.out.println("hyp post: "+hyps[i]);
                if (debug) System.out.println("ref: "+texts[i].getReferenceSeg());
            } catch (Exception e){
                e.printStackTrace();
            }
        }        
        return hyps;
    }

    public void setDebug(boolean debug){this.debug=debug;}
    boolean is_windowing_enabled = false;
    boolean debug =false;
    boolean num_segs_known=true;
    String lcseg_path;
}
                
