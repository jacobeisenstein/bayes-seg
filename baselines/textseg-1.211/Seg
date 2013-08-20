#!/bin/csh
#
# Seg [-N -n NUMBER-OF-SEGMENTS -p STEMMER] < textfile > segged-textfile
#

onintr cleanUP

set n = -1
set stemmer = cat
set withNum = ""
while($#argv > 0)
    set arg = $1; shift;
    switch ($arg)
    case -n:
        set n = $1; shift;
        breaksw;
    case -p:
        set stemmer = $1; shift
        breaksw;
    case -N:
        set withNum = "-N";
	breaksw;
    default
	breaksw
    endsw
end

set tmpfile = /tmp/Seg.`whoami`.`uname -n`.$$
set segfile  = $tmpfile.seg
set textfile = $tmpfile.txt
set mydir = $HOME/bayesseg/baselines/textseg-1.211

cat > $textfile
if ( $n == -1 ) then
    cat $textfile | $stemmer | $mydir/prep-seg | $mydir/vseg > $segfile
else
    cat $textfile | $stemmer | $mydir/prep-seg | $mydir/seg -maxNumSegs $n | tail -1 > $segfile
endif

$mydir/seg-comb $withNum $segfile $textfile

cleanUP:
/bin/rm -f $segfile $textfile
