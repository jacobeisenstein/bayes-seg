use File::Basename;
while(<>){
    if(/^=+$/){
	chomp($code = <>);
	chomp($file = <>);
	chomp($Pk = <>);
	chomp($sec = <>);
	($range) =  basename(dirname($file));
	push(@{$Pk{$range}}, $Pk);
	push(@{$sec{$range}}, $sec);
	$numSamples{$range}++;
	push(@{$Pk{total}}, $Pk);
	push(@{$sec{total}}, $sec);
	$numSamples{total}++;
    }
}

print "$code\n";
print "                Pk         CPU Sec.
range snt  mean   std    mean    std\n";
for my $range (sort keys %Pk){
    printf "%5s %d %.4f %.4f %.4f %.4f\n",$range, $numSamples{$range} ,mean(@{$Pk{$range}}), std(@{$Pk{$range}}), mean(@{$sec{$range}}), std(@{$sec{$range}});
}

sub mean {
    my $sum=0;
    for(@_){
	$sum+=$_;
    }
    return $sum/@_;
}

sub std {
    my $av = mean(@_);
    my $v = 0;
    for(@_){
	$v += ($av - $_)**2;
    }
    sqrt($v/(@_-1));
}
