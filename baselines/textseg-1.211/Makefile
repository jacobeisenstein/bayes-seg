all:
	gcc -g -Wall -o seg seg.c -lm
	gcc -g -Wall -o vseg vseg.c -lm
	gcc -g -Wall -o rseg rseg.c -lm
	gcc -g -Wall -o nseg nseg.c -lm

test:
	./prep-seg < data/org/stargazers.mwn.words | ./seg > data/t/stargazers.seg
	./prep-seg < data/org/stargazers.mwn.words | ./vseg > data/t/stargazers.vseg
	./prep-seg < data/org/stargazers.mwn.words | ./rseg > data/t/stargazers.rseg
	./prep-seg < data/org/stargazers.mwn.words | ./nseg > data/t/stargazers.nseg
	cat data/org/stargazers.mwn.words | ./rseg2 -n 9 > data/t/stargazers.rseg2
	diff data/{org,t}/stargazers.seg
	diff data/{org,t}/stargazers.vseg
	diff data/{org,t}/stargazers.rseg
	diff data/{org,t}/stargazers.rseg2
	diff data/{org,t}/stargazers.nseg

	./seg-comb -N data/t/stargazers.seg data/org/stargazers.txt > data/t/stargazers.seg.comb
	./seg-comb -N data/t/stargazers.vseg data/org/stargazers.txt > data/t/stargazers.vseg.comb
	diff data/{org,t}/stargazers.seg.comb
	diff data/{org,t}/stargazers.vseg.comb

	./Seg < data/org/stargazers.mwn.words > data/t/stargazers.Seg
	diff data/{org,t}/stargazers.Seg

testE:
	./pstemmer < data/org/stargazers.txt > data/t/stargazers.pstemmer.words
	./ESeg < data/org/stargazers.txt > data/t/stargazers.eseg
	diff data/{org,t}/stargazers.pstemmer.words 
	diff data/{org,t}/stargazers.eseg

	./pstemmer < data/org/stargazers.mwn.words > data/t/stargazers.mwnp.words
	./prep-seg < data/t/stargazers.mwnp.words | ./vseg > data/t/stargazers.seg1
	./seg-comb -N data/t/stargazers.seg1 data/org/stargazers.txt >data/t/stargazers.seg1.comb
	diff data/{org,t}/stargazers.mwnp.words 
	diff data/{org,t}/stargazers.seg1
	diff data/{org,t}/stargazers.seg1.comb

testJ:
	./cstemmer < data/org/gijiroku.txt > data/t/gijiroku.cstemmer.words
	prep-seg < data/t/gijiroku.cstemmer.words | vseg > data/t/gijiroku.vseg
	./JSeg < data/org/gijiroku.txt > data/t/gijiroku.jseg
	diff data/{org,t}/gijiroku.cstemmer.words
	diff data/{org,t}/gijiroku.vseg
	diff data/{org,t}/gijiroku.jseg

install:
	/bin/cp JSeg cstemmer cstemmer.pl seg vseg rseg nseg prep-seg seg-comb Seg pstemmer ESeg rseg2 /usr/local/bin

clean:
	/bin/rm -f seg vseg rseg nseg data/t/* data/comp/TestLog100.txt data/comp/TestLog110.txt
