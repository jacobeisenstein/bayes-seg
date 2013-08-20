package edu.mit.util.stats;

import cern.jet.stat.Gamma;
import java.util.HashMap;

/* computes the gamma function using the CERN code, but memoizes results */
public interface FastGamma {
    public double logGamma(final double in);
    public double gamma(final double in);
}
