#!/usr/bin/perl -w
#
# A simple content-word selector.
#
# chasen < euc-jp-text | cstemmer.pl
#
# NOTE:
# 
# ChaSen version 2.2.1 is assumed.
# This file should be saved in EUC-JP.
#

@words = ();
while(<>){
    if(/EOS/){
	print "@words\n";
	@words = ();
    }else{
	my ($word,$pron,$lemma,$pos) = split;
	push(@words, $lemma) if isContentWord($lemma,$pos);
    }
}

sub isContentWord {		# refine する必要がある．
    my $lemma = shift;
    my $pos = shift;
    
    # ascii
    if($lemma =~ /^[\000-\177]+$/){ 
	return 0 if $lemma=~/^[^a-zA-Z]+$/;
    }
    
    # not ascii
    return 1 if $lemma=~/^([\245].)+$/;	# 片仮名
    return 0 if $lemma =~ /^((\244.)+)$/; # hiragana sequence
    return 1 if $pos =~ /未知語/;
    
    return 1 if $pos =~ /^記号-アルファベット/;
    return 1 if $pos =~ /^名詞/ && $pos !~/名詞-(数|代名詞|非自立|特殊|接尾|接続|動詞)/;
    return 1 if $pos =~ /^(接頭|名詞-接尾)/;
    return 0;
}
