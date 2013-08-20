/*
  rseg.c --- get the segmentation of the highest probability
  
  Usage:

    prep-seg < INPUT |\
    rseg [-minSpan NUMBER -maxSpan NUMBER] > OUTPUT

  INPUT FORMAT:

    See `prep-seg.'
  
  OUTPUT FORMAT:

	<seg start="START_GAP_ID"  end="END_GAP_ID">
		<seg start="START_GAP_ID"  end="END_GAP_ID">
			<seg start="START_GAP_ID"  end="END_GAP_ID">
			...
			</seg>
		</seg>
		...
	</seg>
	
    The nested structure shows the recursive segmentations. A
    segment with start="s" and end="e" have sentences [s..e),
    assuming that the id of first sentence is 0. For example,
    <seg start="0" end="4"> contains sentence 0, 1, 2, and 3.
  

  OPTIONS:

    See `seg.c'

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

void seg(int minGap, int maxGap, int numTypes, int depth);
double cost(int i, int j, int numTypes, int thisNumTypes, int numAllTokens);

int minSpan=0;
int maxSpan=0;
int **text, *numTokens, **cum, *total;
int *counter;
int *bp;
double **edge, *minCosts;

int
main(int argc, char **argv)
{
  char line[MAX_LINE_CHARS];
  int numTypes=0, numLines=0;
  int i,j,k;
  char *token;

  /* options */
  for(i=1; i<argc; i++){
    if(strcmp(argv[i], "-minSpan")==0){
      minSpan = atoi(argv[++i]);
    }else if(strcmp(argv[i], "-maxSpan")==0){
      maxSpan = atoi(argv[++i]);
    }
  }

  /* get the input */
  scanf("%d %d\n", &numTypes, &numLines);
  numTokens = (int *)malloc(sizeof(int)*numLines);
  text = (int **)malloc(sizeof(int*)*numLines);
  i=-1;
  while(fgets(line, MAX_LINE_CHARS, stdin)!=NULL){
    i++;
    token = strtok(line, " ");
    numTokens[i] = atoi(token);
    text[i] = (int *)malloc(sizeof(int)*numTokens[i]);
    j=-1;
    while((token = strtok(NULL, " ")) != NULL){
      j++;
      text[i][j] = atoi(token);
    }
  }

  /* count occurrences */
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

  /* recursive segmentation */
  printf("<seg start=\"%d\" end=\"%d\">\n",
	 0,numLines);
  edge = (double **)malloc(sizeof(double*)*numLines);
  for(i=0; i<numLines; i++){
    edge[i] = (double *)malloc(sizeof(double)*(numLines+1));
  }
  minCosts = (double *)malloc(sizeof(double)*(numLines+1));
  counter = (int *)malloc(sizeof(int)*(numTypes));
  bp = (int *)malloc(sizeof(int)*(numLines+1));
  seg(0, numLines, numTypes, 1);
  printf("</seg>\n");
  
  return 0;
}

void
seg(int minGap, int maxGap, int numTypes, int depth)
{
  int numLines = maxGap - minGap;
  int i,j,start,end;
  int thisNumTypes=0,thisMinSpan,thisMaxSpan;
  int numGaps;
  int gaps[numLines+1];
  double newCost;
  
  /* count thisNumTypes */
  for(i=0; i<numTypes; i++){
    counter[i]=0;
  }
  for(i=minGap; i<maxGap; i++){
    for(j=0; j<numTokens[i]; j++){
      counter[text[i][j]]++;
    }
  }
  for(i=0; i<numTypes; i++){
    if(counter[i]>0){
      thisNumTypes++;
    }
  }
  
  /* calculate thisMinSpan, thisMaxSpan */
  thisMinSpan = (minSpan>0) ? MIN(minSpan,numLines) : 1;
  thisMaxSpan = (maxSpan>0) ? MIN(maxSpan,numLines) : numLines;
  
  /* edge */
  for(i=minGap; i<maxGap; i++){
    for(j=i+1; j<MIN(maxGap+1, i+thisMinSpan); j++){
      edge[i-minGap][j-minGap] = LONG_MAX;
    }
    for(j=i+thisMinSpan; j<MIN(maxGap+1, i+thisMaxSpan+1); j++){
      edge[i-minGap][j-minGap] = cost(i, j, numTypes, thisNumTypes,
				      total[maxGap]-total[minGap]);
    }
  }

  /* initialization */ 
  for(i=0; i<numLines+1; i++){
    minCosts[i] = LONG_MAX;
  }
  minCosts[0] = 0;
  bp[0] = -1;
  
  /* forward */
  for(start=minGap; start<maxGap; start++){
    for(end=start+1; end<MIN(maxGap+1, start+thisMaxSpan+1); end++){
      newCost = minCosts[start-minGap] + edge[start-minGap][end-minGap];
      if(newCost < minCosts[end-minGap]){
	minCosts[end-minGap] = newCost;
	bp[end-minGap] = start;
      }
    }
  }
  
  /* backward */
  i=maxGap;
  numGaps=0;
  while(i>=0){
    gaps[numGaps++] = i;
    i=bp[i-minGap];
  }
  
  if(numGaps<=2){
    return;
  }
  
  for(i=1; i<numGaps; i++){
    for(j=0;j<depth;j++)printf("\t");
    printf("<seg start=\"%d\" end=\"%d\">\n",
	   gaps[numGaps-i],gaps[numGaps-i-1]);
    seg(gaps[numGaps-i], gaps[numGaps-i-1], numTypes, depth+1);
    for(j=0;j<depth;j++)printf("\t");
    printf("</seg>\n");
  }
}

double
cost(int i, int j, int numTypes, int thisNumTypes, int numAllTokens)
{
  double DL=0.0,n,t;
  int k;

  DL = log(numAllTokens);
  for(k=0; k<numTypes; k++){
    n = cum[j][k] - cum[i][k];
    t = total[j] - total[i];
    if(n>0){
      DL += n * log((t+thisNumTypes)/(n+1));
    }
  }
  return DL;
}
