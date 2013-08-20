package edu.mit.nlp.segmenter;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;

public class Segment {
    int[] m_counts;
    int m_total;
    int m_dur;
    int D;
    boolean m_fresh;
    double m_prior;
    double[] m_pdur;
    double m_ll = 0;
    public Segment(int p_D){
        D = p_D;
        m_counts = new int[D];
        m_total = 0;
        m_dur = 0;
        m_fresh = false;
        m_prior = 0;
        m_pdur = null;
        m_ll =0;
    }

    public Segment(Segment seg){
        m_counts = new int[seg.D];
        D = seg.D;
        for (int i = 0; i < seg.D; i++) m_counts[i] = seg.m_counts[i];
        m_dur = seg.m_dur;
        m_fresh = seg.m_fresh;
        m_prior = seg.m_prior;
        m_total = seg.m_total;
        if (seg.m_pdur != null){
            m_pdur = new double[seg.m_pdur.length];
            for (int i = 0; i < m_pdur.length; i++)
                m_pdur[i] = seg.m_pdur[i];
        }
        m_ll = seg.m_ll;
    }

    public void setPDur(double[] pdur){
        m_pdur = new double[pdur.length];
        for (int i = 0; i < pdur.length; i++) m_pdur[i] = pdur[i];
        m_fresh = false;
    }

    /**
       @return the MAP estimate of the language model for the segment
    **/
    public double[] getTheta(){
	double[] out = new double[D];
	for (int i = 0; i < D; i++)
	    out[i] = (m_counts[i] + m_prior) / (m_prior*D + m_total);
	return out;
    }

    public void addCounts(double[] counts){
        m_dur++;
        for (int i = 0; i < D; i++) {m_counts[i] += counts[i]; m_total += counts[i]; }
        m_fresh = false;
    }
    public void subCounts(double[] counts){
        m_dur--;
        for (int i = 0; i < D; i++) {m_counts[i] -= counts[i]; m_total -= counts[i]; assert(m_counts[i]>=0);}
        m_fresh = false;
    }
    public int getDur() {return m_dur;}
}
