package edu.mit.util.stats;
import java.util.List;
import java.util.ArrayList;
import cern.jet.stat.Gamma;
import cern.jet.math.Arithmetic;

public class Stats {
    /** 
	Computes the KL divergence of the distribution.  Actually
	it's the skewed divergence, from Lee 2001
    **/
    public static double kldiv ( double distrib1[], double distrib2[]){
	double total = 0;
	double alpha = .99;
	for (int i = 0; i < distrib1.length; i++){
	    // normal KL-divergence
	    //	    total += distrib1[i] * (log (distrib1[i]) - log(distrib2[i]));
	    // skewed KL-divergence
	    double q = 0.99 * distrib1[i] + 0.01 * distrib2[i];
	    double r = distrib2[i];
	    if (r != 0)
		total += r * (Math.log(r) - Math.log(q));
	    //	    System.out.println(distrib1[i]+" "+distrib2[i]);
	}
	//System.out.println ("--- "+total+" ---");
	return total;
    }
//     public static <T extends Object> List<T> make(T first) {
//         return new List<T>(first);
//     }
    
    public static <T extends Object>  List<List<T>> generateOrderings(List<T> stuff){
        List<List<T>> output = new ArrayList<List<T>>();
        if (stuff.size() == 1) {
            output.add (stuff);
        }
        else {
            for (int i = 0; i < stuff.size(); i++){
                ArrayList<T> copy = new ArrayList<T>(stuff);
                T removed = copy.remove(i);
                List<List<T>> sublists = generateOrderings(copy);
                for (List<T> sublist : sublists){
                    sublist.add(stuff.get(i));
                    output.add (sublist);
                }
            }
        }
        return output;
    }
    public static double myLogNBinPdf(int k, double r, double p){
	return Gamma.logGamma(r+k) - Gamma.logGamma(r) - 
	    Arithmetic.logFactorial(k) + r * Math.log(p) + k * Math.log(1-p);
    }

    //m = edur
    //k = dispersion
    public static double myLogNBinPdf2(int z, double m, double k){
        return Gamma.logGamma(m*k+z) - Arithmetic.logFactorial(z) - Gamma.logGamma(m*k) + 
            m*k*(Math.log(k/(k+1))) - z*Math.log(k+1);
    }

    //this thing is bogus
    public static double myLogGammaPdf(double x, double mean, double variance){
        //the problem is the parameters
	double a = mean * mean / variance;
	double b = variance / mean;
        //the log gamma calculation is correct tho
        //this is correct
        double out = (a-1) * Math.log(x) - (x / b) - Gamma.logGamma(a) - a * Math.log(b);
        //System.out.println(String.format("mu=%.3f var=%.3f a=%.3f b=%.3f out=%.3f",mean,variance,a,b,out));
        //System.exit(0);
        return out;
    }

    /**
       computeMultinomial
       
       builds the expected multinomial given counts and a symmetric dirichlet
       theta = argmax_theta p(theta | counts, prior) 
       = argmax_theta p(counts | theta) p(theta | prior)
       
       this is in closed form due to conjugacy       
    **/
    public static double[] computeMultinomial(final double[] counts, double prior){
        double[] output = new double[counts.length];
        double sum = 0;
        for (int i = 0; i < counts.length; i++){
            sum += counts[i] + prior;
            output[i] = counts[i] + prior;
        }
        for (int i = 0; i < counts.length; i++)
            output[i] /= sum;
        return output;
    }
    
    public static double[] computeLogMultinomial(final double[] counts, double prior){
        double[] multi = computeMultinomial(counts, prior);
        double[] output = new double[counts.length];
        for (int i =0; i < multi.length; i++)
            output[i] = Math.log(multi[i]);
        return output;
    }
    
    /**
       computes the log-probability of a bag-of-words observation x, given
       the multinomial probability distribution a
     **/
    public static double logProbMultinomial(final double[] x, final double[] a){
        double output = 0;
        //could use caching log here, whatever
        for (int i = 0; i < x.length; i++)
            output += x[i] * Math.log(a[i]);
        return output;
    }
    
    public static double logProbLogMultinomial(final double[] x, final double[] loga){
        double output = 0;
        for (int i =0; i < x.length; i++)
            output += x[i]*loga[i];
        return output;
    }
       
    /* compute the gradient of the dispersion parameter for a negative binomial distribution, wrt a single observation */
    public static double computeDispersionGradient(int len, double mean_len, double log_dispersion, FastDigamma digamma){
        //         System.out.println(String.format("computing dispersion gradient: %d %d %.3f %.3f",len,T,mean_len,log_dispersion));
        if (log_dispersion > MAX_LOG_DISPERSION) log_dispersion = MAX_LOG_DISPERSION;
        double dispersion = Math.exp(log_dispersion);
        double dispersion_times_mean_len = dispersion * mean_len;
        return dispersion * (mean_len * 
                             (digamma.digamma(dispersion_times_mean_len + len) - 
                              digamma.digamma(dispersion_times_mean_len) +
                              Math.log(dispersion / (dispersion + 1)))
                             + (mean_len - len) / (dispersion + 1));
    }
    

    public static void main (String argv[]){
        List<String> list = new ArrayList<String>();
        list.add("1");
        list.add("2");
        list.add("3");
        list.add("4");
        List<List<String>> orderings = generateOrderings(list);
        for (List<String> sublist : orderings){
            for (String string : sublist){
                System.out.print (string+" ");
            }
            System.out.println();
        }
    }
    public static double MAX_LOG_DISPERSION = 10;
}
