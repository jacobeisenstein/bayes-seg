package edu.mit.util.stats;

import cern.jet.stat.Descriptive;
import cern.colt.list.DoubleArrayList;
import java.text.DecimalFormat;

public class Results {
    public int true_positives = 0;
    public int false_positives = 0;
    public int true_negatives = 0;
    public int false_negatives = 0;
    //probably won't want to use this to preserve sigma calculations
    public void combine (Results r){
	true_positives = true_positives + r.true_positives;
	true_negatives = true_negatives + r.true_negatives;
	false_positives = false_positives + r.false_positives;
	false_negatives = false_negatives + r.false_negatives;
	//return output;
    }
    public static Results combine (Results r1, Results r2){
	Results output = new Results();
	output.true_positives = r1.true_positives + r2.true_positives;
	output.true_negatives = r1.true_negatives + r2.true_negatives;
	output.false_positives = r1.false_positives + r2.false_positives;
	output.false_negatives = r1.false_negatives + r2.false_negatives;
	return output;
    }
    public double accuracy () { 
	return ((double) true_positives + true_negatives)/
	    ((double) false_positives + false_negatives + 
	     true_positives + true_negatives);
    }
    public double recall ()  {
	//	System.err.println ("WRONG RECALL");
	return ((double) true_positives) / 
	    ((double) true_positives + false_negatives);
    }
    public double precision () {
	//System.err.println ("WRONG PRECISION");
	return ((double) true_positives) / 
	    ((double) true_positives + false_positives);
    }
    public double fMeasure ()  {
	//System.err.println ("WRONG FMEASURE");
	return (2f * (double) true_positives) / 
	    (2f * (double) true_positives + false_negatives + false_positives); 
	//	    return (2f * precision() * recall ()) / (precision() + recall()); 
    }
    public double falseAlarm (){
	return (double) false_positives / ((double)true_positives +
					   true_negatives +
					   false_positives + 
					   false_negatives);
    }
    public double score(int desiderata){
	if (desiderata == ACCURACY) return accuracy();
	if (desiderata == FMEASURE) return fMeasure();
	return 0;
    }
    public String toString(){
	DecimalFormat format = new DecimalFormat();
        format.setMaximumFractionDigits(3);
        format.setMinimumFractionDigits(3);
	return "Accuracy: "+format.format (accuracy())+" "+
	    "Recall: "+format.format(recall())+" "+
	    "Precision: "+format.format(precision())+" "+
	    "F-Measure: "+format.format(fMeasure())+" "+
	    "False Alarm: "+format.format(falseAlarm());
    }
//     public int total(){
//         return true_negatives + false_negatives + true_positives + false_positives;
//     }
    public int total(){ return true_positives + false_negatives; }
    public static final int ACCURACY = 1;
    public static final int FMEASURE = 2;
}
