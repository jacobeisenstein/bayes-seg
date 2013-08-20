## convert an icsi transcript + segmentation to a choi-style transcript with segmentation boundaries inline with the text

require 'rexml/document'
#require 'rexml/streamlistener'

include REXML

prefix = ARGV[0]
$trans_dir = "transcripts/transcripts/"
$seg_dir = "segs/"
$out_dir = "choi_sentence/"
$trans_suff = ".mrt"
$seg_suff = ".ref"
$out_suff = ".ref"
$extra_suff = ".xtra"

all_sents = Array.new
all_segs = Array.new

def recurse_all_text(element)
  out = ""
  element.children.each{|kiddo|
    if (kiddo.kind_of? Text)
      out << kiddo.value().chomp()
    elsif (kiddo.kind_of? Element)
      out << recurse_all_text(kiddo)
    end
  }
  out.chomp
end

class Sentence
  def initialize(segment)
    @starttime = segment.attribute('StartTime').to_s.to_f
    @endtime = segment.attribute('EndTime').to_s.to_f
    @speaker = segment.attribute('Participant').to_s
    @segtext = recurse_all_text(segment)
  #elim false starts -- words that end in hyphen
    @segtext.gsub!(/[a-zA-Z]+-\s/,"")
  #elim underbars
    @segtext.gsub!(/_/,"")
  #elim newlines, extra spaces
    @segtext.gsub!(/\n/,"")  
    @segtext.gsub!(/\s+/," ")
    @segtext.gsub!(/^\s/,"")
    @segtext.gsub!(/\s$/,"")
  end
  def starttime
    @starttime
  end
  def text
    @segtext
  end
  def speaker
    @speaker
  end
  def endtime
    @endtime
  end
end

#setup the out file
out_file = File.new($out_dir+prefix+$out_suff,"w")
xtra_file = File.new($out_dir+prefix+$extra_suff,"w")

#read in the transcript
trans_doc = Document.new(File.new($trans_dir+prefix+$trans_suff))
all_sents_elems = trans_doc.root.get_elements('/Meeting/Transcript/Segment')
all_sents_elems.each{|segment|
  all_sents.push(Sentence.new(segment))
}

#read in the segmentation
IO.foreach($seg_dir+prefix+$seg_suff) do |line|
  cols = line.split("\s")
  if (cols.length == 1 || (cols[1]!="digits" && cols[1]!="cut"))
    all_segs.push([cols[0].to_f,true]) #this segment is printable
#    puts "pushing #{cols[0].to_f}"
    if (cols.length > 1)
      puts "unknown segment type: #{cols[1]}"
    end
  else
#    puts "not pushing #{cols[0].to_f} #{cols[1]}"
    all_segs.push([cols[0].to_f,false]) #this segment is not printable
  end
end

#nowwwww... go through the segs.  print the splitter.  then print all sents that come before the next one
all_sents.sort! {|a,b| a.starttime <=> b.starttime}
all_segs.sort! {|a,b| a[0] <=> b[0]}
sent_ctr = 0
prev_valid_sent = nil
for i in 1..all_segs.length
  if (i < all_segs.length):next_seg_start = all_segs[i][0]
  else
    next_seg_start = 1000000000
  end  
  linebreak=false
  if (all_segs[i-1][1])
    out_file.puts "=========="
    all_sents.each{|sent|
      text = sent.text
      if (sent.starttime >= all_segs[i-1][0] && sent.starttime < next_seg_start && text.gsub(/\s/,"").length != 0)
        out_file.puts sent.text.gsub(/[\.\?]/,"\n")
#        out_file.printf("%s\n",sent.text.gsub(/[\.\?]/," "))
        if (prev_valid_sent != nil)
          if (sent.speaker != prev_valid_sent.speaker)
#             out_file.printf("\n");
#             linebreak=true
          else
#             linebreak=false
          end
          xtra_file.puts "#{sent.starttime-prev_valid_sent.endtime} #{sent.speaker != prev_valid_sent.speaker}"
        end
        prev_valid_sent = sent
      end
    }
#     if (!linebreak) 
#       out_file.puts
#     end
  end
end
out_file.puts "=========="
