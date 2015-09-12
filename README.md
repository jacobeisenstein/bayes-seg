bayes-seg
=========

Java code from the 2008 EMNLP paper "Bayesian Unsupervised Topic Segmentation" by Eisenstein and Barzilay

Copyright (C) 2008 Massachusetts Institute of Technology
 
    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.
 
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
 
    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

Contents
----------

The directory contents of this distribution are as follows:

- **eval** A unix script for evaluating a segmenter.
- **segment** unix script for segmenting text
- **build.xml** An ant script for building the source
- **log.config** A configuration file for java logging used by MinCutSeg. Must remain in the base directory.
- **README.md** This documentation
- **./baselines** A directory containing alternative segmenters that can
  be evaluated with this code.  due to licensing restrictions,
  this contains only Utiyama and Isahara's TextSeg-1.211, which
  they kindly provide on their website.
- **./classes** The compiled java class files
- **./config** Configuration files for running various experiments
  + *dp.config* Runs the dynamic programming lexical cohesion system described in the paper
  + *cue.config* Runs the cue-phrase MCMC implementation of our system,
	initializing from the results of the dynamic programming                
	implementation
  + *mcsopt.ai.config* Evaluates the MinCutSeg segmenter (Malioutov & Barzilay 2006)
  + *lcseg.config* Evaluates the LCSeg segmenter.  This segmenter is not included
	  in this distribution, but may be obtained from Columbia University using
	  this [license](http://www1.cs.columbia.edu/nlp/licenses/LCSegLicenseDownload.html).
  + *ui.config* Evaluates Utiyama & Isahara's segmenter
  + *perfect.config* Runs a "perfect" segmenter that reproduces the ground truth.  For debugging.
  + *STOPWORD.list* List of stop words used.  From Malioutov's MinCutSeg package.
  + *CUEPHRASES.hl* List of possible cue phrases.  Based on (Hirschberg and Litman 1993)
- **./data** The medical textbook dataset.  Each file is a chapter from the book,
  and the chapters are divided into segments using the "Choi" 
  notation, with segment boundaries indicated by a row of equal signs.
  Other punctuation is stripped out.
- **./doc** The Javadoc API
- **./lib** Library dependencies
  + colt.jar
  + lingpipe-3.4.0.jar
  + log4j-1.2.14.jar
  + MinCutSeg.jar
  + mtj.jar
  + options.jar
- **./source** The java source files
- **./results** The result output

This is a java-based, platform-independent implementation.  The class files
provided here require Java Runtime Environment (JRE) 6.0 or higher.  If you
have a lower version, you may recompile by running "ant rebuild."  They have
been tested to run when compiled to JRE 5.0.

Usage: reproducing results
----------

This system contains code and data necessary to reproduce the "textbook"
results from the paper.  To evaluate a system, type

./eval config/CONFIGFILE

CONFIGFILE indicates the name of the configuration file.  A separate configuration
file is included for each segmenter, as described above.

The command will evaluate the segmenter the following files from the textbook
dataset: files 001.ref, 101.ref, and 201.ref.  You can modify the "eval"
script to evaluate on other sets of files.

The system will output: 

A. The set of all options
B. The location of all data files
C. Information specific to the segmenter.  
D. The Pk and WindowDiff for each data file
E. The average Pk and WindowDiff
 
See the javadoc for edu.mit.nlp.segmenter.mcmc.CuCoSeg.printStatus() 
for details on the status output of the MCMC segmenter.

Usage: segmenting your own text
-----------

To segment a text, provide it through stdin:

cat filename | ./segment config/CONFIGFILE

For example, the command

cat data/books/clinical/050.ref | ./segment config/dp.config

will run the Bayesian cohesion segmenter with dynamic programming on
the text file 050.ref.  It will output the indices of the last sentences
in each topic segment.

The number of segments is read from the file itself.  The "segment" script
shows how to change the desired number of segments, and how to get debug
output.  Note that the MCMC cue phrase segmenter was not really intended to
be run on individual documents, and may not work well for this purpose.

Building Instructions
------------

The build system uses Jakarta Ant framework (http://jakarta.apache.org/ant/).

To build in unix, set your current working directory to the root directory of
the distribution, where the file "build.xml" is located, and enter the
command "ant build", optionally followed by target name (build, clean, save or docs)

This code is copyright 2008 by the Massachusetts Instute of Technology

Hierarchical Segmentation
=====================

The hierarchical segmenter from the paper 
J. Eisenstein. Hierarchical text segmentation from multi-scale lexical cohesion. In Proceedings of the North American Chapter of the Association for Computational Linguistics (NAACL), Boulder, CO, 2009. 

is available as a release
https://github.com/jacobeisenstein/bayes-seg/releases/tag/v1.0
