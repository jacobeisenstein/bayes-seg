package edu.mit.util.stats;

import java.util.ArrayList;
import java.text.DecimalFormat;
import cern.jet.stat.Descriptive;
import cern.colt.list.DoubleArrayList;

/**
   Tracks SegmentResults across multiple runs or trials or whatever...
 *
 * @author <a href="mailto:jacobe@csail.mit.edu">Jacob Eisenstein</a>
 * @version 1.0
 */
public class ResultTracker extends Results {
    public ResultTracker (){
	recs = new ArrayList<Double>();
	precs = new ArrayList<Double>();
	fas = new ArrayList<Double>();
	fms = new ArrayList<Double>();
	accs = new ArrayList<Double>();
        sizes = new ArrayList<Double>();
    }
    
    public void combine (Results r){
	super.combine(r);
	accs.add (new Double(r.accuracy()));
	fms.add (new Double(r.fMeasure()));
	recs.add (new Double(r.recall()));
	precs.add (new Double(r.precision()));
	fas.add (new Double(r.falseAlarm()));
        sizes.add (new Double(r.total()));
    }

    public void combine (ResultTracker r){
	super.combine((Results)r);
	for (int i =0 ; i < r.size(); i++){
	    recs.add (new Double (r.all_recall()[i]));
	    precs.add (new Double (r.all_precision()[i]));
	    fms.add (new Double (r.all_fMeasure()[i]));
	    fas.add (new Double (r.all_falseAlarm()[i]));
	    accs.add (new Double (r.all_accuracy()[i]));	    
            sizes.add (new Double (r.sizes.get(i)));
	}
    }

    public Results averageRepeatedMeasures (){
        Results results = new Results();
        int divisor = sizes.size();
        results.true_positives = true_positives / divisor;
        results.true_negatives = true_negatives / divisor;
        results.false_positives = false_positives / divisor;
        results.false_negatives = false_negatives / divisor;
        return results;
    }

    public double[] all_accuracy(){
	return makeDoubleArray(accs);
    }

    public double[] all_fMeasure(){
	return makeDoubleArray (fms);
    }

    public double[] all_recall(){
	return makeDoubleArray (recs);
    }
    public double[] all_precision(){
	return makeDoubleArray (precs);
    }
    public double[] all_falseAlarm(){
	return makeDoubleArray (fas);
    }

    public int size(){ return accs.size(); }

    public static double[] toDoubleArray (ArrayList<Double> v){
	double[] output = new double[v.size()];
	int ctr = 0;
	for (Double d : v){
	    output[ctr] = d;
	    ctr++;
	}
	return output;
    }

    //yadda yadda yadda
    public String matlabString(){
        DecimalFormat format = new DecimalFormat();
        format.setMaximumFractionDigits(3);
        format.setMinimumFractionDigits(3);
	DoubleArrayList i_fms = new DoubleArrayList(toDoubleArray(fms));
	double mean = Descriptive.mean(i_fms);
        return ""+format.format(100 * fMeasure()) + " "+
	    format.format(100 * mean) + " "+
            format.format(100 * Math.sqrt(Descriptive.sampleVariance(i_fms,mean))/ fms.size());
    }

    //computes a weighted mean, given the sizes as weights
    public double mean (double[] data){
        double total = 0;
        double weight = 0;
        for (int i = 0; i < data.length; i++){
            assert data != null;
            assert sizes != null;
            total += data[i] * sizes.get(i);
            weight += sizes.get(i);
        }
        return total / weight;
    }

    //computes the weighted variance, using the sizes as the weights
    public double variance (double[] data){
        double total = 0;
        double weight = 0;
        double mean = mean (data);
        for (int i = 0; i < data.length; i++){
            total += (data[i] - mean) * (data[i] - mean) * sizes.get(i);
            weight += sizes.get(i);
        }
        return total / weight;
    }

    /**
     * outputs t-score that the distributions of fmeasures are different.
     * 
     * These results check out with those produced at the website:
     http://www.physics.csbsju.edu/cgi-bin/stats/Paired_t-test
     * @param x A set of results
     * @param y A set of results
     * @return t-score that the distributions of fmeasures are different
     */
    public static double pairedTTest (ResultTracker x, ResultTracker y){
	double x_mean = x.fMeasure();
	double y_mean = y.fMeasure();
	double mean_diff = x_mean - y_mean;
	int dof = x.fms.size();
	double diff_variance = 0;
        double sum_of_prod_of_weights = 0;
	for (int i = 0; i < x.fms.size(); i++){
	    diff_variance += (x.fms.get(i) - y.fms.get(i) - mean_diff) * 
		(x.fms.get(i) - y.fms.get(i) - mean_diff) * x.sizes.get(i) * y.sizes.get(i);
            sum_of_prod_of_weights += x.sizes.get(i) * y.sizes.get(i);
            System.out.printf ("%f %f %d %d\n", x.fms.get(i), y.fms.get(i), 
                               (int) x.sizes.get(i).doubleValue(), (int) y.sizes.get(i).doubleValue());
	}
        System.out.println ();
        diff_variance /= sum_of_prod_of_weights;        
	double t_score = mean_diff / Math.sqrt (diff_variance / (dof-1));
	return t_score;
    }


    protected double[] makeDoubleArray (ArrayList<Double> v){
	double output[] = new double[v.size()];
	for (int i = 0; i < v.size(); i++){
	    output[i] = v.get(i);
	}
	return output;
    }
    protected String getString (ArrayList<Double> v){
	double data[] = makeDoubleArray (v);
	DecimalFormat format = new DecimalFormat();
	format.setMaximumFractionDigits(3);
	format.setMinimumFractionDigits(3);
	
	//return ""+format.format(Utils.mean (data))+" ("+
	//   format.format(Math.sqrt (Utils.variance (data)/v.size()))+")";
	DoubleArrayList i_data = new DoubleArrayList(data);
	double mean = Descriptive.mean(i_data);
	return ""+format.format(mean)+" ("+
	    format.format(Math.sqrt (Descriptive.sampleVariance (i_data, mean)/v.size()))+")";
    }
    public String toString (){
	DecimalFormat format = new DecimalFormat();
	format.setMaximumFractionDigits(3);
	format.setMinimumFractionDigits(3);
	return "Rec: "+format.format(recall())+" "+getString (recs)+" "+
	    "Prec: "+format.format(precision())+" "+getString (precs)+" "+
	    "F-M: "+format.format(fMeasure())+" "+getString (fms)+" "+
       	    "Acc: "+format.format(accuracy())+" "+getString (accs);//+" "+
	    //"F-A: "+Utils.doubleToString(falseAlarm(),3)+" "+getString (fas);
    }


    ArrayList<Double> accs;
    ArrayList<Double> recs;
    ArrayList<Double> precs;
    ArrayList<Double> fms;
    ArrayList<Double> fas;
    ArrayList<Double> sizes;

    
    public static void main (String argv[]){
	ResultTracker tracker = new ResultTracker();
	Results r1 = new Results();
	r1.true_positives = 100;
	r1.true_negatives = 1000;
	r1.false_positives = 150;
	r1.false_negatives = 50;
	System.out.println(r1.toString());

	Results r2 = new Results();
	r2.true_positives = 11;
	r2.true_negatives = 80;
	r2.false_positives = 12;
	r2.false_negatives = 5;
	System.out.println(r2.toString());

	tracker.combine(r1);
	System.out.println(tracker.toString());
	tracker.combine(r2);
	System.out.println(tracker.toString());
    }

}


