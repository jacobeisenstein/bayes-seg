package edu.mit.multimodal.motifs;

import edu.mit.nlp.segmenter.*;
import java.io.* ;
import ml.options.Options;
import java.util.*;
import edu.mit.nlp.util.Utils;

public class MultiEval extends SegmentationScore {
    public static void main(String args[]){
        String usageMsg = "Usage: MultiEval -ref ref_file -hyp hyp_file [-suf suffix]";
        Options opt = new Options(args, 0);

        opt.getSet().addOption("ref", Options.Separator.BLANK, Options.Multiplicity.ONCE);
        opt.getSet().addOption("hyp", Options.Separator.BLANK, Options.Multiplicity.ONCE);
        opt.getSet().addOption("suf", Options.Separator.BLANK, Options.Multiplicity.ONCE);

        if (!opt.check() || !opt.getSet().isSet("ref") || !opt.getSet().isSet("hyp")) {
            System.out.println(opt.getCheckErrors());
            System.out.println(usageMsg);
            System.exit(1);
        }

        String refDir = opt.getSet().getOption("ref").getResultValue(0);
        String hypDir = opt.getSet().getOption("hyp").getResultValue(0);

        File file = new File(configFile);
        Utils.ensure(file.canRead(), "File: " + configFile + " is not accessible!");
        MinCutSeg.setConfigFile(configFile);
        MinCutSeg.globalInit();
        MinCutSeg segmenter = new MinCutSeg();
        segmenter.loadSegmenterParams();

        //go through the dirs and stuff
        FilenameFilter myfilt = new FilenameFilter(){
                public boolean accept(File dir, String name){
                    return name.endsWith(".ref") || name.endsWith(".dev");
                }
            };
        //apparently these come back in no particular order. fuckers.
        String[] refFiles = new File(refDir).list(myfilt);
        String[] hypFiles = new File(hypDir).list(myfilt);
        Arrays.sort(refFiles);
        Arrays.sort(hypFiles);

        System.out.println(String.format("%d %d",refFiles.length,hypFiles.length));
        double total_pk = 0; double total_wd = 0;
        for (int i = 0; i < refFiles.length; i++){
            TextWrapper refText = segmenter.loadText(refDir+"/"+refFiles[i]);
            TextWrapper hypText = segmenter.loadText(hypDir+"/"+hypFiles[i]);
            
            List hypSeg = hypText.getReferenceSeg();
            List refSeg = refText.getReferenceSeg();

            System.out.print(refFiles[i]+"\t"+hypFiles[i]);
            
            
            if (refSeg == null || hypSeg == null || refSeg.isEmpty() || hypSeg.isEmpty()) {
                System.out.println("Error: The input text segmentations are invalid!");
                System.exit(1);
            }
//             int refLastBound = ((Integer) refSeg.get(refSeg.size() - 1)).intValue();
//             int hypLastBound = ((Integer) hypSeg.get(hypSeg.size() - 1)).intValue();
            
//             if (refLastBound != hypLastBound) {
//                 System.out.println("Mismatch in the number of sentences in the reference and hypothesis texts!");
//                 System.exit(1);
//             }
            
            double pk = calcErrorProbablity(hypSeg, refSeg, refText, "Pk");
            double wd = calcErrorProbablity(hypSeg, refSeg, refText, "WD");
            
            total_pk += pk; total_wd += wd;

            System.out.println("\t"+pk + "\t" + wd);
        }
        System.out.println(String.format("%.5f %.5f",total_pk/refFiles.length,total_wd/refFiles.length));
    }
    public static final String configFile = "/afs/csail/u/j/jacobe/motifs/baselines/MinCutSeg/config/physics.config";
}
