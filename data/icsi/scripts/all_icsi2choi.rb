filenames = Dir.glob(ARGV[0])
filenames.each{|filename|
  prefix = filename.split("/")[-1].split(".")[0]
  puts prefix
  puts `ruby icsi2choi.rb #{prefix}`
}
