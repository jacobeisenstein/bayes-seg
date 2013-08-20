package edu.mit.nlp.segmenter;

public interface InitializableSegmenter extends Segmenter {
    public void initSegs(String segfilename); //this may do nothing
}
