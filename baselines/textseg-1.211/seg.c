/*
  seg.c  --- get the segmentation of the highest probability

  Usage:

    prep-seg < INPUT |\
    seg [-minSpan NUMBER -maxSpan NUMBER -maxNumSegs NUMBER] > OUTPUT


  INPUT FORMAT:

    See `prep-seg.'


  OUTPUT FORMAT:

    If an input text has N lines then, the output will consists
    of N lines. The format of the i-th (1<=i<=n) output line
    is:
    
	COST gap(0) gap(1) ... gap(i)

    where COST is the cost of the optimum segmentation with i
    segments and gap(j) is the j-th gap of the segmentation.
    
    
    If gap(i) = k, then the i-th gap is between the k-th line
    and (k+1)-th line. An example is:
    
        Gaps   0       1       2       3       4       5       6
	Lines    line1   line2   line3   line4   line5   line6
	
    If an output line is:

        COST 0 2 5 6

    then, the lines are segmented as

        line1 line2
	line3 line4 line5
	line6

    If the line id starts with 0 then, the lines are segmented as

        line0 line1
	line2 line3 line4
	line5

    We (naturally) use the latter defenition of the sentence id
    in this program.

    The (global) best segmetations is the minimum cost segmetaion.


  OPTIONS:

      -minSpan NUMBER		minimum number of lines in a segments
      -maxSpan NUMBER		maximum number of lines in a segments
      -maxNumSegs NUMBER	maximum number of segments

      
  COMPUTATIONAL COMPLEXITY:

	O(numLines^3 * numTypes)
	
	
  Copyright (C) 2000 UTIYAMA Masao <mutiyama@crl.go.jp>
  All rights reserved.

*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <limits.h>

#define MIN(a,b) ((a)<(b)?(a):(b))
#define MAX_LINE_CHARS	20000

/*#define DEBUG*/

typedef struct {
  double cost;
  int *path;
} Message;

double cost(int i, int j, int **cum, int *total, int numTypes, int numAllTokens); 

int
main(int argc, char **argv)
{
  char line[MAX_LINE_CHARS];
  int numTypes=0, numLines=0, numAllTokens=0;
  int **text, *numTokens, **cum, *total, *bp;
  double **edge, *minCosts, newCost;
  int i,j,k,start,end;
  int maxSpan=0, minSpan=0, maxNumSegs=0;
  char *token;
  Message *messages;

  /* options:
     -minSpan
     -maxSpan
     -maxNumSegs
  */
  for(i=1; i<argc; i++){
    if(strcmp(argv[i], "-minSpan")==0){
      minSpan = atoi(argv[++i]);
    }else if(strcmp(argv[i], "-maxSpan")==0){
      maxSpan = atoi(argv[++i]);
    }else if(strcmp(argv[i], "-maxNumSegs")==0){
      maxNumSegs = atoi(argv[++i]);
    }
  }
  
  /*
    numTypes		number of types of words in the input
    numLines		number of lines in the input
  */
  
  scanf("%d %d\n", &numTypes, &numLines);
#ifdef DEBUG
  fprintf(stderr, "numTypes=%d, numLines=%d\n", numTypes, numLines);
#endif
  
  /*
    numTokens[i]	number of tokens of words in the text
    text[i][j]		number of tokens of words in the j-th line
  */
  numTokens = (int *)malloc(sizeof(int)*numLines);
  text = (int **)malloc(sizeof(int*)*numLines);
  
  /* read the input */
  i=-1;
  while(fgets(line, MAX_LINE_CHARS, stdin)!=NULL){
    /* number of words in this line */
    i++;
    token = strtok(line, " ");
    numTokens[i] = atoi(token);
    text[i] = (int *)malloc(sizeof(int)*numTokens[i]);

    /* word ids */
    j=-1;
    while((token = strtok(NULL, " ")) != NULL){
      j++;
      text[i][j] = atoi(token);
    }
  }

  /*
    cum[i][k]	cumulative frequency of word-k up-to gap-i.
    total[i]	\sum_k cum[i][k]
  */
  cum = (int **)malloc(sizeof(int*)*(numLines+1));
  total = (int *)malloc(sizeof(int)*(numLines+1));
  for(i=0; i<numLines+1; i++){
    cum[i] = (int *)malloc(sizeof(int)*numTypes);
    for(k=0; k<numTypes; k++){
      cum[i][k] = 0;
    }
    total[i] = 0;
  }
  for(i=0; i<numLines; i++){
    for(k=0; k<numTypes; k++){
      cum[i+1][k] = cum[i][k];
      total[i+1] += cum[i][k];
    }
    for(j=0; j<numTokens[i]; j++){
      cum[i+1][text[i][j]]++;
    }
    total[i+1] += numTokens[i];
  }
  numAllTokens = total[numLines];

#ifdef DEBUG
  for(i=0; i<numLines+1; i++){
    fprintf(stderr, "gap=%d, total=%d, id:freq, ", i, total[i]);
    for(k=0; k<numTypes; k++){
      if(cum[i][k] > 0){
	fprintf(stderr, "%d:%d ", k, cum[i][k]);
      }
    }
    fprintf(stderr, "\n");
    if(i<numLines){
      for(j=0; j<numTokens[i]; j++){
	fprintf(stderr, "%d ", text[i][j]);
      }
      fprintf(stderr,"\n");
    }
  }
  fprintf(stderr, "numAllTokens = %d\n", numAllTokens);
#endif

  /* parameters */
  minSpan = (minSpan>0) ? MIN(minSpan,numLines) : 1;
  maxSpan = (maxSpan>0) ? MIN(maxSpan,numLines) : numLines;
  maxNumSegs = (maxNumSegs>0) ? MIN(maxNumSegs,numLines) : numLines;
#ifdef DEBUG
  fprintf(stderr, "minSpan=%d, maxSpan=%d, maxNumSegs=%d\n", minSpan, maxSpan, maxNumSegs);
#endif
  
  /*
    edge[i][j]	cost of the segment between gap-i and gap-j
  */
  
  edge = (double **)malloc(sizeof(double*)*numLines);
  for(i=0; i<numLines; i++){
    edge[i] = (double *)malloc(sizeof(double)*(numLines+1));
    for(j=i+1; j<MIN(numLines+1, i+minSpan); j++){
      edge[i][j] = LONG_MAX;
    }
    for(j=i+minSpan; j<MIN(numLines+1, i+maxSpan+1); j++){
      edge[i][j] = cost(i, j, cum, total, numTypes,numAllTokens);
#ifdef DEBUG
      fprintf(stderr, "%d ==(%.3f)==> %d\n", i, edge[i][j], j);
#endif
    }
    
    edge[i][i]=0;
  }
  
  /*
    messages[i] = message at gap-i
  */
  messages = (Message *)malloc(sizeof(Message)*(numLines+1));
  for(i=0; i<numLines+1; i++){
    if(i<=maxSpan){
      messages[i].cost = edge[0][i];
    }else{
      messages[i].cost = LONG_MAX;
    }
    messages[i].path = (int *)malloc(sizeof(int)*(i+2));
    messages[i].path[0] = 0;
    messages[i].path[1] = i;
  }
  if(maxSpan==numLines){
    printf("%f 0 %d\n", messages[numLines].cost, numLines);
  }
  minCosts = (double *)malloc(sizeof(double)*(numLines+1));
  bp = (int *)malloc(sizeof(int)*(numLines+1));
  
  /* stretch segments one step */
  for(i=1; i<maxNumSegs; i++){
#ifdef DEBUG
    printf("================ %d ================\n", i);
#endif
    /* cost initialization */
    for(end=i+1; end<numLines+1; end++){	
      minCosts[end] = LONG_MAX;
    }
    /* optimum path */
    for(start=i; start<numLines; start++){
      for(end=start+1; end<MIN(numLines+1, start+maxSpan+1); end++){
	newCost = messages[start].cost + edge[start][end];
#ifdef DEBUG
	printf("%d ==> %d, (%f + %f = %f)\n", start, end, messages[start].cost, edge[start][end], newCost);
#endif
	if(newCost < minCosts[end]){
	  minCosts[end] = newCost;
	  bp[end] = start;
	}
      }
    }
    /* renew messages */
    for(end=numLines; end>=i+1; end--){
      messages[end].cost = minCosts[end];
      for(j=0; j<i+1; j++){
	messages[end].path[j] = messages[bp[end]].path[j];
      }
      messages[end].path[i+1] = end;
    }
    if(messages[numLines].cost < LONG_MAX){
      /* print out */
      printf("%f", messages[numLines].cost);
      for(j=0; j<i+2; j++){
	printf(" %d",messages[numLines].path[j]);
      }
      printf("\n");
    }
  }
  
  return 0;
}

double
cost(int i, int j, int **cum, int *total, int numTypes, int numAllTokens)
{
  double DL=0.0,n,t;
  int k;

  DL = log(numAllTokens);
  for(k=0; k<numTypes; k++){
    n = cum[j][k] - cum[i][k];
    t = total[j] - total[i];
    if(n>0){
      DL += n * log((t+numTypes)/(n+1));
    }
  }
  return DL;
}
