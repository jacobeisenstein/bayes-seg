#
# perl diff.pl data/comp/TestLog100.txt.org data/comp/TestLog100.txt
#

$org = shift;
$new = shift;

open(O, $org) or die "$org:$!";
while(<O>){
    if(/^=+$/){
	chomp($code = <O>);
	chomp($file = <O>);
	chomp($Pk = <O>);
	chomp($sec = <O>);
	$Pk{$file} = $Pk;
    }
}

open(N, $new) or die "$new:$!";
while(<N>){
    if(/^=+$/){
	chomp($code = <N>);
	chomp($file = <N>);
	chomp($Pk = <N>);
	chomp($sec = <N>);
	unless(defined $Pk{$file} && $Pk{$file} == $Pk){
	    print "====== $file ====\n";
	    print "$org: $Pk{$file}\n";
	    print "$new: $Pk\n";
	}
    }
}
