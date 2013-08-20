/*
  vseg.c --- get the segmentation of the highest probability

  Usage:

    prep-seg < INPUT |\
    vseg [-minSpan NUMBER -maxSpan NUMBER] > OUTPUT


  INPUT FORMAT:

    See `prep-seg.'


  OUTPUT FORMAT:

    The output is the best segmentation of INPUT and its format is
  
	COST gap(0) gap(1) ... gap(k)

    if it is segmented into k segments. See `seg.c' for the
    details of the definition of `gap.'


  OPTIONS:

    See `seg.c'

  COMPUTATIONAL COMPLEXITY:

	O(numLines^2 * numTypes)

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

double cost(int i, int j, int **cum, int *total, int numTypes, int numAllTokens); 

int
main(int argc, char **argv)
{
  char line[MAX_LINE_CHARS];
  int numTypes=0, numLines=0, numAllTokens=0;
  int **text, *numTokens, **cum, *total, *bp, *gaps;
  double **edge, *minCosts, newCost;
  int i,j,k,start,end,numGaps;
  int minSpan=0,maxSpan=0;
  char *token;
  
  /* options */
  for(i=1; i<argc; i++){
    if(strcmp(argv[i], "-minSpan")==0){
      minSpan = atoi(argv[++i]);
    }else if(strcmp(argv[i], "-maxSpan")==0){
      maxSpan = atoi(argv[++i]);
    }
  }
  
  /*
    numTypes		number of types of words in the input
    numLines		number of lines in the input
  */
  
  scanf("%d %d\n", &numTypes, &numLines);
  
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
  
  /* parameters */
  minSpan = (minSpan>0) ? MIN(minSpan,numLines) : 1;
  maxSpan = (maxSpan>0) ? MIN(maxSpan,numLines) : numLines;
  
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
    }
  }
  
  /* initialization */
  minCosts = (double *)malloc(sizeof(double)*(numLines+1));
  bp = (int *)malloc(sizeof(int)*(numLines+1));
  for(i=0; i<numLines+1; i++){
    minCosts[i] = LONG_MAX;
  }
  minCosts[0] = 0;
  
  /* forward */
  bp[0]=-1;
  for(start=0; start<numLines; start++){
    for(end=start+1; end<MIN(numLines+1, start+maxSpan+1); end++){
      newCost = minCosts[start] + edge[start][end];
      if(newCost < minCosts[end]){
	minCosts[end] = newCost;
	bp[end] = start;
      }
    }
  }
  
  /* backward */
  gaps = (int *)malloc(sizeof(int)*(numLines+1));
  i=numLines;
  numGaps=0;
  while(i>=0){
    gaps[numGaps++] = i;
    i=bp[i];
  }
  printf("%f",minCosts[numLines]);
  for(i=0; i<numGaps; i++){
    printf(" %d", gaps[numGaps-i-1]);
  }
  printf("\n");
  
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
