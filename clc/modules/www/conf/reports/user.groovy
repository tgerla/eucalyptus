for( int i = 0; i < 20; i++ ) {
  def u = new UserReportInfo() {{
          userName = "test-${i}" 
          imageCount = i
        }};
  results.add( u )
}
println "HELLOOOO ${results}"

def class UserReportInfo {
  String userName;
  Integer imageCount;
}
