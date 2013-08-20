package edu.mit.nlp.segmenter;

import java.util.ArrayList;
import java.lang.Math;
import java.util.Arrays;

/**
   Keeps track of counts and segments.  Somewhat redundant with {@link edu.mit.util.ling.CountsManager}, this 
   should be reconciled.
 **/
public class Document {
    /**
       @param sents an matrix representation of the document.  there are <code>sents.length</code> sentences, and
       each row of <code>sents</code> is an array of size W (the size of the vocabulary).
       @param N is the number of segments.  it's a shame that you have to prespecify it.
     **/
    public Document(double[][] sents, int N){
        T = sents.length;       
        D = sents[0].length;
        D2 = 0;
        this.N = N;
        m_words = sents;
        m_segpoints = new int[N+1];

        //initial equal-width segmentation
        m_segpoints[0] = 0;
	for (int i = 1; i < N; i++) m_segpoints[i] = Math.round(T * i / N);
	m_segpoints[N] = T;

        m_segments = new Segment[N];

	m_prior = .1;

	//the previous values, for when we reject a move
        m_next_segments = new Segment[2];

        for (int i = 0; i < N; i++){
            m_segments[i] = new Segment(D);
            for (int j = m_segpoints[i]; j < m_segpoints[i+1]; j++){
                m_segments[i].addCounts(m_words[j]);
                for (int k = 0; k < m_words[j].length; k++){
                    if (m_words[j][k] > 0) D2++;
                }
            }
        }
        //        printDurs();
    }

    /** print the durations of each segment.  i forget why this was important to do.
     **/
    protected void printDurs(){
        System.out.print(T+": ");
        int mycount =0;
        for (int i = 0; i < N; i++) {
            System.out.print(m_segments[i].getDur()+" ");
            mycount += m_segments[i].getDur();
        }
        assert(mycount == T);
        System.out.println();
    }

    /** @return the number of segments **/
    public double N(){ return N;}
    /** @return the number of sentences **/
    public double T(){ return T;}
    /** @return the number of words in the vocabulary **/
    public double D(){ return m_words[0].length; }
    /** @return the number of words with non-zero counts **/
    public double D2(){ return D2; } 

    /**
       @return a vector of the segmentation points
    **/
    public int[] getSPs(){
	int[] out = new int[m_segpoints.length];
	for (int i = 0; i < m_segpoints.length; i++){
	    out[i] = m_segpoints[i];
	}
	return out;
    }

    public void setPDur(double[] pdur){
        for (int i = 0; i < N; i++){
            m_segments[i].setPDur(pdur);
        }
    }

    /**
       @return the map estimate of the language model for each segment
    **/
    public double[][] getThetas(){
	double[][] thetas = new double[N][];
	for (int i = 0; i < N; i++){
	    thetas[i] = m_segments[i].getTheta();
	}
	return thetas;
    }

    public double[][] m_words;
    Segment[] m_segments;
    Segment[] m_next_segments;
    int[] m_segpoints;
    double m_prior;
    int D, T, N;
    int D2; //num words that actually have counts
}
