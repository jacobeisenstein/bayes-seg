package edu.mit.util.weka;

/**
   This wraps the RISO Limited Memory BFGS quasi-newton optimization package.
   It attempts to follow the same API as the Weka optimization package,
   for convenience.
**/

import riso.numerical.*;

public abstract class LBFGSWrapper {
    protected int m_max_its = 200;
    //not sure how this parameter comes into play
    public double m_eps = 1.0e-3; //smaller?
    protected double xtol = 1.0e-16; //estimate of machine precision.  get this right
    //number of corrections, between 3 and 7
    //a higher number means more computation and time, but more accuracy, i guess
    public int m_num_corrections = 3; 

    protected double[] m_estimate;
    protected double m_value;
    protected int m_num_parameters;
    protected boolean m_debug;
    
    public LBFGSWrapper(int num_parameters){ 
        m_num_parameters = num_parameters;
        m_estimate = new double[m_num_parameters];
    }
   
    public abstract double objectiveFunction(double[] x) throws Exception;
    public abstract double[] evaluateGradient(double[] x) throws Exception;   

    public double[] getVarbValues(){ return m_estimate; }
    public double getMinFunction(){ return m_value; }

    /** 
        setEstimate
        Use this to initialize the search
    **/
    public void setEstimate(double[] estimate){
        m_estimate = new double[estimate.length];
        System.arraycopy(estimate,0,m_estimate,0,estimate.length);
    }
    public void setDebug(boolean debug){m_debug = debug;}
    public void setMaxIteration(int max_its){m_max_its = max_its;}

    public double[] findArgmin () throws Exception{
        double[] diagco = new double[m_num_parameters];
        int[] iprint = new int[2];
        iprint[0] = m_debug?1:-1;  //output at every iteration (0 for 1st and last, -1 for never)
        iprint[1] = 3; //output the minimum level of info
        int[] iflag = new int[1];
        iflag[0] = 0;
        double[] gradient = new double[m_num_parameters];
        int iteration = 0;
        do {
            m_value = objectiveFunction(m_estimate);
            gradient = evaluateGradient(m_estimate);
            LBFGS.lbfgs( m_num_parameters,
			 m_num_corrections, 
			 m_estimate,
			 m_value,
			 gradient, 
			 false, //true if we're providing the diag of cov matrix Hk0 (?)
			 diagco, //the cov matrix
			 iprint, //type of output generated
			 m_eps,
			 xtol, //estimate of machine precision
			 iflag //i don't get what this is about
			 );
            iteration++;
        } while (iflag[0] != 0 && iteration <= m_max_its);
        return m_estimate;
    }

}
