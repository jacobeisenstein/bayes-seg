package edu.mit.util.stats;

import java.util.Arrays;

public class FastDCM {
    public FastDCM(double prior, int W){
        this.prior = prior;
        this.W = W;
        gamma = new FastDoubleGamma();
         log_gamma_W_prior = gamma.logGamma(W * prior);
         W_log_gamma_prior = W * gamma.logGamma(prior);
        local_gamma = true;
    }
    public FastDCM(double prior, int W, boolean expect_ints){
        this.prior = prior;
        this.W = W;
        gamma = expect_ints?new FastIntGamma(W,prior):new FastDoubleGamma();
        log_gamma_W_prior = gamma.logGamma(W * prior);
        W_log_gamma_prior = W * gamma.logGamma(prior);
        local_gamma = true;
    }
    public FastDCM(double prior, int W, FastGamma gamma){
        this.prior = prior;
        this.W = W;
        this.gamma = gamma;
         log_gamma_W_prior = gamma.logGamma(W * prior);
         W_log_gamma_prior = W * gamma.logGamma(prior);
        local_gamma = false;
    }
    //maybe also code up the Elkan version?
    public double logDCM(double[] counts){
        double output = log_gamma_W_prior - W_log_gamma_prior;
        assert (W == counts.length);
        if (Math.abs(W_log_gamma_prior - gamma.logGamma(prior) * W) > .0001){
            System.out.println(String.format("believed: %.4e ; true %.4e", W_log_gamma_prior, gamma.logGamma(prior)*W));
            System.out.println(String.format("W = %d/%d",W,counts.length));
            System.out.println(((FastIntGamma)gamma).getOffset());
        }
        double N = 0;
        for (int i = 0; i < counts.length; i++){
            N += counts[i] + prior;
            output += gamma.logGamma(counts[i]+prior);
        }
        output -= gamma.logGamma(N);
        return output;
    }
    //this isn't great coding practice to repeat the body of the double version
    //but it's likely faster this way
    public double logDCM(int[] counts){
        double output = log_gamma_W_prior - W_log_gamma_prior;
        assert (W == counts.length);
        if (Math.abs(W_log_gamma_prior - gamma.logGamma(prior) * W) > .0001){
            System.out.println(String.format("believed: %.4e ; true %.4e", W_log_gamma_prior, gamma.logGamma(prior)*W));
            System.out.println(String.format("W = %d/%d",W,counts.length));
            System.out.println(((FastIntGamma)gamma).getOffset());
        }
        double N = 0;
        for (int i = 0; i < counts.length; i++){
            N += counts[i] + prior;
            output += gamma.logGamma(counts[i]+prior);
        }
        output -= gamma.logGamma(N);
        return output;
    }
    public void setPrior(double prior){
        this.prior = prior;
        if (gamma instanceof FastIntGamma) ((FastIntGamma)gamma).setOffset(prior);
        log_gamma_W_prior = gamma.logGamma(W * prior);
        W_log_gamma_prior = W * gamma.logGamma(prior);
    }
    public double getPrior(){ return prior; }
    public void setGamma(FastGamma gamma){ this.gamma = gamma; }
    public FastGamma getGamma(){ return gamma; }
    int W;
    double prior;
    //these terms are constant unless the number of segments is changing
     double log_gamma_W_prior;
     double W_log_gamma_prior;
    FastGamma gamma;
    boolean local_gamma;
}
