package edu.mit.util.stats;

public class Annealer {
    public Annealer(double burnin_duration, double cooling_duration, double max_burnin, int num_its){
        this.burnin_duration = burnin_duration;
        this.cooling_duration = cooling_duration;
        this.max_burnin = max_burnin;
        this.num_its = num_its;
//         System.out.println(String.format("annealer created: %.2f %.2f %.2f %d",
//                                          burnin_duration,
//                                          cooling_duration,
//                                          max_burnin,
//                                          num_its));
        it_num = 0;
    }
    public void reset(){ it_num = 0; }

    /** calls the annealer, adds one tick **/
    public double anneal(double prob){
        double out = annealWithoutUpdate(prob);
        it_num++;
        return out;
    }
    /** returns f(.5) given the current temp.
        seems more useful than the temperature
    **/
    public double getHalfProbAnnealed(){
        return annealWithoutUpdate(.5);
    }

    public double annealWithoutUpdate(double prob){
        double temperature = 1;
	int burnin_end = (int) (burnin_duration * num_its);
	int cooling_start = (int) ((1 - cooling_duration) * num_its );
	
	if (it_num < burnin_end){
	    temperature = 1 + ((double) max_burnin - 1) * (1 - (double) it_num / (double) burnin_end);
	}
	if (it_num > cooling_start){
	    temperature = ((double) num_its - it_num + 1) / ((double) num_its - cooling_start);
	}
//         System.out.println(String.format("%d %f %f %f",it_num,temperature,Math.pow(prob,1/temperature),max_burnin));
	return Math.pow(prob, 1 / temperature);
    }

    public void update(){
        it_num++;
    }

    double burnin_duration;
    double cooling_duration;
    double max_burnin; //starting value of the burnin temperature
    int it_num;
    int num_its;
}
