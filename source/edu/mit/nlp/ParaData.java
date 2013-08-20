package edu.mit.nlp;

import java.io.*;
import java.util.ArrayList;

/**
   Stores paralingual data:
   whether there's a speaker change between each turn
   silence between each turn
   maybe other stuffs later
 **/
public class ParaData {
    public ParaData(File file) throws Exception {
        BufferedReader in = new BufferedReader(new FileReader(file));
        ArrayList<Double> pauses = new ArrayList<Double>();
        ArrayList<Boolean> changes = new ArrayList<Boolean>();
        System.out.println("loading "+file.getPath());
        while (in.ready()){
            String line = in.readLine();
            String[] dudes = line.split(" ");
            try {
                pauses.add (new Double(dudes[0]));
                changes.add (new Boolean(dudes[1]));
            } catch (Exception e){
                System.err.println("could not parse line: "+line);
                e.printStackTrace();
                System.exit(0);
            }
        }
        pause_by_sent = new double[pauses.size()];
        speaker_change_by_sent = new boolean[changes.size()];
        for (int i = 0 ;i < pauses.size(); i++){
            pause_by_sent[i] = pauses.get(i);
            speaker_change_by_sent[i] = changes.get(i);
            //            System.out.println(String.format("READING: %.1f %b",pause_by_sent[i],speaker_change_by_sent[i]));
        }
    }
    public double[] getPauses(){ return pause_by_sent; }
    public boolean[] getSpeakerChange() { return speaker_change_by_sent; }
    double[] pause_by_sent;
    boolean[] speaker_change_by_sent;
}
