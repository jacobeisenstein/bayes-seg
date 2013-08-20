package edu.mit.util.stats;

import cern.jet.stat.Gamma;
import java.util.HashMap;
import java.util.Arrays;

/* computes the gamma function using the CERN code, but memoizes results */
public class FastIntGamma implements FastGamma {
    private double[] log_gamma_cache;
    private boolean[] has_log_gamma;
    private double[] gamma_cache;
    private boolean[] has_gamma;
    double offset;
    public FastIntGamma(int maxval){ 
        initialize(maxval,0);
    }
    public FastIntGamma(int maxval, double offset){ 
        initialize(maxval,offset);
    }
    protected void initialize(int maxval, double offset){
        this.offset = offset;
        log_gamma_cache = new double[maxval];
        gamma_cache = new double[maxval];
        has_log_gamma = new boolean[maxval];
        has_gamma = new boolean[maxval];
        Arrays.fill(log_gamma_cache,-1f);
        Arrays.fill(gamma_cache,-1f);
        Arrays.fill(has_log_gamma,false);
        Arrays.fill(has_gamma,false);
    }
    public synchronized double logGamma(final double p_in){
        double out=0;
        double in = p_in - offset;
        if (in != (int) in || in >= log_gamma_cache.length ) out= Gamma.logGamma(in);
        else {
            if (!has_log_gamma[(int)in]) {
                log_gamma_cache[(int)in] = Gamma.logGamma(in+offset);
                has_log_gamma[(int)in] = true;
            }
            out = log_gamma_cache[(int)in];
        }
        return out;
    }
    public synchronized double gamma(final double p_in){
        double out=0;
        double in = p_in - offset;
        if (in != (int) in || in >= gamma_cache.length ) out= Gamma.logGamma(in);
        else {
            if (!has_gamma[(int)in]) {
                gamma_cache[(int)in] = Gamma.logGamma(in+offset);
                has_gamma[(int)in] = true;
            }
            out = gamma_cache[(int)in];
        }
        return out;
    }    
    public void setOffset(double offset){
        if (offset != this.offset){
            this.offset = offset;
            Arrays.fill(has_gamma,false);
            Arrays.fill(has_log_gamma,false);        
        }
    }
    public double getOffset(){ return offset; }
}
