// This program is based on excerpts from C99-1.2-release.tgz
import java.util.Vector;
import uk.ac.man.cs.choif.extend.Argx;
import uk.ac.man.cs.choif.extend.Arrayx;
import uk.ac.man.cs.choif.extend.Debugx;
import uk.ac.man.cs.choif.extend.Stringx;
import uk.ac.man.cs.choif.extend.Vectorx;
import uk.ac.man.cs.choif.extend.io.LineInput;
import uk.ac.man.cs.choif.extend.io.LineOutput;
import uk.ac.man.cs.choif.extend.structure.ContextVector;
import uk.ac.man.cs.choif.extend.structure.EntropyVector;
import uk.ac.man.cs.choif.nlp.surface.Punctuation;
import uk.ac.man.cs.choif.nlp.surface.Stemmer;
import uk.ac.man.cs.choif.nlp.surface.WordList;

import uk.ac.man.cs.choif.nlp.statistics.distribution.*;
import uk.ac.man.cs.choif.extend.Statx;

public class PStemmer {

public final static void main(String[] args) {
    String[][] D = Stringx.tokenize(LineInput.load(new LineInput(System.in)), " ");
    WordList stopword = WordList.stopwordList();
    LineOutput out = new LineOutput(System.out);
    for(int i=0; i<D.length; i++){
	for(int j=0; j<D[i].length; j++){
	    String token = D[i][j].toLowerCase();
	    if (Punctuation.isWord(token) && !stopword.has(token)) {
		String stem = Stemmer.stemOf(token);
		System.out.print(stem);
		System.out.print(" ");
	    }
	}
	out.println("");
    }
}
}

