package edu.mit.nlp.segmenter;

import edu.mit.util.JacobUtil;

public class SegEval {
    /* pkEval
       
    We expect my_seg_points to indicate a bunch of segmentation points
    And trueseg to list segment index for each sentence, ranging from 1 to N
    */
    public static double pkEval(int[] my_seg_points, int[] zstar){
	int k = (int) Math.round((double) zstar.length / (2 * zstar[zstar.length-1]));
	int[] zhat = getZHat(my_seg_points, zstar);
	double num_misses = 0.0;
	for (int i = k; i < zstar.length; i++){
	    if ((zstar[i] == zstar[i-k]) != (zhat[i] == zhat[i-k])) num_misses++;
	}
	return num_misses / (zstar.length - k);
    }

    public static double wdEval(int[] my_seg_points, int[] zstar){
	int k = (int) Math.round((double) zstar.length / (2 * zstar[zstar.length-1]));
	int[] zhat = getZHat(my_seg_points, zstar);
	double num_misses = 0.0;
	for (int i = k; i < zstar.length; i++){
	    num_misses += Math.abs((zhat[i] - zhat[i-k]) - (zstar[i] - zstar[i-k]));
	}
	return num_misses / (zstar.length - k);
    }

    //assume that the last segpoint is the end
    public static int[] getZStar(int[] tru_seg_points){
        int zstar[] = new int[tru_seg_points[tru_seg_points.length-1]];
        int ctr = 1;
        for (int i = 0; i < zstar.length; i++){
            if (i >= tru_seg_points[ctr-1]) ctr = ctr + 1;
            zstar[i] = ctr;
        }
        return zstar;
    }

    public static int[] getZHat(int[] my_seg_points, int[] zstar){
    	int zhat[] = new int[zstar.length];
	int ctr = 1;
	//	for (int i = 0; i < my_seg_points.length; i++) System.out.println(my_seg_points[i]);
	for (int i = 0; i < zstar.length; i++){
	    if (ctr - 1 < my_seg_points.length && i >= my_seg_points[ctr-1]){
		ctr = ctr + 1;
	    }
	    zhat[i] = ctr;
	    //	    System.out.print(zhat[i]+" ");
	} //System.out.println();
	return zhat;
    }
    public static void main(String argv[]){
	int sps1[] = {2, 5, 10};
	int sps2[] = {6, 10};
	int sps3[] = {7, 8, 9};
	int sps4[] = {1, 12, 13, 14};
	int zstar[] = {1,1,1,1,2,2,2,3,3,3,3,3,4,4,4};
        int zhat1[] = getZStar(new int[]{2,5,10,15});
        System.out.println("FOOBAR");
        System.out.println(JacobUtil.formatArray("%d"," ",zhat1));
	System.out.println (pkEval(sps1,zstar)+" "+wdEval(sps1,zstar));
	System.out.println (pkEval(sps2,zstar)+" "+wdEval(sps2,zstar));
	System.out.println (pkEval(sps3,zstar)+" "+wdEval(sps3,zstar));
	System.out.println (pkEval(sps4,zstar)+" "+wdEval(sps4,zstar));
    }
}
