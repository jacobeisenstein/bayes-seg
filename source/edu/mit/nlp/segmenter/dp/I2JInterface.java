package edu.mit.nlp.segmenter.dp;

import edu.mit.nlp.segmenter.TextWrapper;
import java.util.List;
import java.util.ArrayList;

/**
   class for converting between my segmenter's data structures and those from Igor's MinCutSeg
**/

public class I2JInterface {
    public static DPDocument makeDPDoc(TextWrapper textwrapper){
        double[][] w = textwrapper.createWordOccurrenceTable(); //D x T matrix 
        double[][] m_words = new double[w[0].length][w.length];
        for (int i = 0; i < w.length; i++)
            for (int j = 0;j < w[i].length; j++)
                m_words[j][i] = w[i][j];
        //get the true segmentation in there
        return new DPDocument(m_words,textwrapper.getReferenceSeg().size()+1,true); //set this false for MAP
    }
    public static int[] getTrueSegs(TextWrapper textwrapper){
        List refseg = textwrapper.getReferenceSeg();
        int[] output = new int[textwrapper.getSentenceCount()];
        refseg.add(textwrapper.getSentenceCount()); //remove it later
        int j = 0;
        for (int i = 0; i < refseg.size(); i++){
            for ( ; j < ((Integer) refseg.get(i)).intValue(); j++){
                output[j] = i+1;
            }
        }
        refseg.remove(refseg.size()-1);
        return output;
    }
    public static List makeIgorList(int[] boundaries, TextWrapper text, boolean windows){
        List out = new ArrayList();
        //in my notation the first dude always comes out to be zero
        for (int i = 1; i < boundaries.length; i++){
            out.add(boundaries[i]);
        }
        out.add(windows?text.getWindowCount():text.getSentenceCount());
        return out;
    }
    public static List makeListFormattedResponse(int[] segids){
        List list = new ArrayList();
        for (int i = 1; i < segids.length; i++){
            if (segids[i] != segids[i-1]) list.add(i);
        }
        return list;
    }

}
