package edu.mit.nlp.segmenter;

import java.io.*;
import java.util.Properties;

public class SegTesterParams {    
    public SegTesterParams(File file) throws Exception {
        //read from this file is some semi-intelligent way
        this.props = new Properties();
        props.load(new FileInputStream(file));
        initialize();
    }
    public SegTesterParams(Properties props) throws Exception {
        this.props = props;
        initialize();
    }
    private void initialize() throws Exception {
        use_choi_style_bounds = getBoolProp(props,"use-choi-style-boundaries",true);
        is_windowing_enabled = getBoolProp(props,"use-fixed-blocks",false);
        is_stems_enabled = getBoolProp(props,"use-word-stems",true);
        window_size = (int)getDoubleProp(props,"window-size",20);
        is_remove_stop_words = getBoolProp(props,"remove-stop-words",true);
        stopword_filename = props.getProperty("stop-words");
        segmenter = props.getProperty("segmenter");
    }

    public static boolean getBoolProp(Properties props,String key, boolean def) {
        String propval=props.getProperty(key,def?"true":"false");
        return propval.equals("true");
    }
    public static int getIntProp (Properties props,String key, int def) throws Exception {
        return Integer.parseInt(props.getProperty(key,String.format("%d",def)));
    }
    public static double getDoubleProp (Properties props,String key, double def) throws Exception {
        return Double.parseDouble(props.getProperty(key,String.format("%f",def)));
    }
    public Properties getProps(){ return props; }
    boolean use_choi_style_bounds = true; public boolean useChoiStyleBounds(){ return use_choi_style_bounds; }
    boolean is_windowing_enabled = false; public boolean isWindowingEnabled(){ return is_windowing_enabled; }
    boolean is_stems_enabled = true; public boolean isStemsEnabled(){ return is_stems_enabled; }
    int window_size = 20; public int getWindowSize(){ return window_size; }
    boolean is_remove_stop_words = true; public boolean isRemoveStopWords(){ return is_remove_stop_words; }
    String stopword_filename = "/afs/csail/u/j/jacobe/motifs/baselines/MinCutSeg/config/STOPWORD.list";
    String getStopWordsFile(){ return stopword_filename; }    
    String segmenter = "edu.mit.multimodal.multimodal.dp.BayesWrapper"; public String segmenter(){ return segmenter; }
    Properties props;
}
