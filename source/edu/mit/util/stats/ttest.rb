require "GSL"
include GSL
#load "~/ruby-gsl-0.2.0/samples/array.rb"

def getData(file)
  re = Regexp.new('^(0\.[0-9]*) ([1-9][0-9]*\.[0-9]*)');
  ctr = 0
  scores = Array.new
  sizes = Array.new
  while (line = file.gets)
    md = re.match(line)
    if (md != nil && md[1].length > 0)
      scores[ctr] = md[1].to_f;
      sizes[ctr] = md[2].to_f;
      ctr = ctr+1
    end
  end
  return [scores, sizes]
end

data1 = getData(File.new(ARGV[0],"r"))
data2 = getData(File.new(ARGV[1],"r"))
if (data1[1] != data2[1])
  puts "Sizes are different.  Likely bogus"
  puts data1[1]
  puts 
  puts data2[1]
end
diff = Array.new
for i in (0..data1[0].size-1)
  diff[i] = data1[0][i] - data2[0][i]
#  puts diff[i]
end
#note the use of weighted statistics
meandiff = GSL::Stats::wmean(data1[1],1,diff,1)
#puts meandiff
sddiff = GSL::Stats::wsd(data1[1],1,diff,1)
dof = data1[0].size - 1
ssd = sddiff / Math::sqrt(dof)
t = meandiff / ssd
#note that although these values don't check out with excel, they do check out with matlab
#note also that it's one tailed
puts "t(#{dof}) = #{t}"
#hmm... why not get these values from somewhere?
if (t.abs > 2.947)
  puts "p < .01 (dof = 15, 2-tailed)"
else
  if (t.abs > 2.131)
    puts "p < .05 (dof = 15, 2-tailed)"
  end
end
