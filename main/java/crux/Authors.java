package crux;

final class Authors {
  // TODO: Add author information.
  static final Author[] all = {new Author("AN", "99999999", "AN"),
                        new Author("TC", "00000000", "TC")};
}

final class Author {
  final String name;
  final String studentId;
  final String uciNetId;

  Author(String name, String studentId, String uciNetId) {
    this.name = name;
    this.studentId = studentId;
    this.uciNetId = uciNetId;
  }
}
