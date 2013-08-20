package edu.mit.nlp.segmenter;

import java.util.List;
import edu.mit.nlp.MyTextWrapper;

/**
   If you want to have a segmenter be evaluated in {@link SegTester}, you must
   write a wrapper that implements this interface.  It's easy.
 **/
public interface Segmenter {
    /**
       Do whatever initialize you need from this config file 
       
       @param config_filename the path to the config file
    **/
    public void initialize(String config_filename);

    /**
       segment a bunch of texts.  we do this jointly in case you want to 
       do processing across the whole set of texts together

       @param texts the array of texts
       @param num_segs the number of segments per text
       @return a list of arrays of segmentation points
    **/
    public List[] segmentTexts(MyTextWrapper[] texts, int[] num_segs);

    /**
       tells your d00d to set its debug flag 
    **/
    public void setDebug(boolean debug);
}
