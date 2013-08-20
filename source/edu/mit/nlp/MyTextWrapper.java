package edu.mit.nlp;

import edu.mit.nlp.segmenter.TextWrapper;
import edu.mit.nlp.ling.LexMap;
import edu.mit.nlp.util.Utils.WindowType;
import java.util.ArrayList;
import java.util.List;
import mt.Matrix;

public class MyTextWrapper extends TextWrapper {
    public MyTextWrapper(String fname){
        super(fname);
        useStems_ = false;
        useTags_ = false;
    }
    
    public void parse(boolean useStems){
        this.useStems_ = useStems;
        super.parse(useStems);
    }

    public void parseWindows(int windowLength, boolean useStems) {
        useStems_ = useStems;
        super.parseFile2(getTextFilename(), WindowType.WINDOW, windowLength, useTags_, useStems);
    }

    public int[][] createSparseWordOccurrenceTable(){
        return createSparseWordOccurrenceTable(getLexMap());
    }

    public int[][] createSparseWordOccurrenceTable(LexMap outmap){
        int[][] t= new int[getText().size()][];
        for (int i = 0; i < getText().size(); i++){
            ArrayList window = (ArrayList) getText().get(i);
            t[i] = new int[window.size()];
            for (int j = 0; j < window.size(); j++){
                if (useStems_){
                    String stem = getLexMap().getStem(((Integer)window.get(j)).intValue());
                    t[i][j] = outmap.getStemId(stem);
                } else {
                    String surface = getLexMap().getWord(((Integer)window.get(j)).intValue());
                    t[i][j] = outmap.getWordId(surface);
                }
            }
        }
        return t;
    }

    public int[][] createWordOccurrenceTable(LexMap outmap){
        int[][] t = new int[getText().size()][useStems_?outmap.getStemLexiconSize():outmap.getWordLexiconSize()];
        //go through the text
        for (int i = 0; i < getRawText().size(); i++){
            ArrayList window = (ArrayList) getText().get(i);
            for (int j = 0; j < window.size(); j++){
                if (useStems_){
                    String stem = getLexMap().getStem(((Integer)window.get(j)).intValue());
                    t[i][outmap.getStemId(stem)]++;
                } else {
                    String surface = getLexMap().getWord(((Integer)window.get(j)).intValue());
                    t[i][outmap.getWordId(surface)]++;
                }
            }
        }
        return t;
    }
    public double[][] createWordOccurrenceTable(List wordList){
        return createWordOccurrenceTable(createWordOccurrenceMatrix2(wordList));
    }

    private double[][] createWordOccurrenceTable(Matrix occurMatrix) {
        int[] rows = new int[occurMatrix.numRows()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }
        int[] cols = new int[occurMatrix.numColumns()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }
        double[][] occurTable = new double[occurMatrix.numRows()][occurMatrix.numColumns()];
        occurMatrix.get(rows, cols, occurTable);
        return occurTable;
    }

    public static int[] getNumSegs(MyTextWrapper[] texts){
        int[] num_segs = new int[texts.length];
        for (int i = 0; i < texts.length; i++){
            num_segs[i] = texts[i].getReferenceSeg().size();
        }
        return num_segs;
    }
    
    public void useTags() {
        useTags_ = true;
        super.useTags();
    }

    public boolean getUseStems(){ return useStems_; }
    public boolean getUseTags(){ return useTags_; }

    private boolean useStems_;    
    private boolean useTags_;
}
