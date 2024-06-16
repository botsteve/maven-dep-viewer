package org.example.tasks;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.example.exception.DepViewerException;

@Slf4j
public class CheckoutTagsTask {

  private static final String NO_TAG_FOUND = "No tags found matching the pattern for repo %s with version %s";

  public static String checkoutTag(File file, String version) throws GitAPIException, IOException {
    try (Git git = Git.open(file)) {
      List<Ref> filteredSortedTags = git.tagList().call().stream()
                                         .filter(tag -> isMatchingVersionTag(version, tag)) // Starts with version
                                         .sorted(new TagComparator(git))
                                         .toList();

      log.debug("Available tags: {}", filteredSortedTags.stream()
                                         .map(Ref::getName)
                                         .collect(Collectors.joining(", ")));

      if (!filteredSortedTags.isEmpty()) {
        Ref tagToCheckout = filteredSortedTags.getFirst();
        checkoutTag(git, tagToCheckout);
        return tagToCheckout.getName();
      } else {
        var depViewerException = new DepViewerException(String.format(NO_TAG_FOUND, file.getName(), version));
        log.error("No tags found matching the pattern.", depViewerException);
        throw depViewerException;
      }
    }
  }

  private static boolean isMatchingVersionTag(String version, Ref tag) {
    var strippedTag = tag.getName().replace("refs/tags/", "").toLowerCase();
    var normalized = normalizeVersion(strippedTag);
    log.debug("Original tag: {} vs Normalized tag: {}", tag.getName(), normalized);
    return normalized.startsWith(version.toLowerCase());
  }

  static class TagComparator implements Comparator<Ref> {

    private final Git git;

    public TagComparator(Git git) {
      this.git = git;
    }

    @Override
    public int compare(Ref tag1, Ref tag2) {
      try {
        Date date1 = getTagDate(tag1);
        Date date2 = getTagDate(tag2);
        return date2.compareTo(date1);
      } catch (IOException e) {
        throw new RuntimeException("Failed to compare tags", e);
      }
    }

    private Date getTagDate(Ref tag) throws IOException {
      try (RevWalk walk = new RevWalk(git.getRepository())) {
        ObjectId objectId = resolveObjectId(tag);
        RevCommit commit = walk.parseCommit(objectId);
        return commit.getCommitterIdent().getWhen();
      }
    }

    private ObjectId resolveObjectId(Ref tag) {
      // Handle annotated tags
      if (tag.getPeeledObjectId() != null) {
        return tag.getPeeledObjectId();
      }
      // Handle lightweight tags
      return tag.getObjectId();
    }
  }


  static void checkoutTag(Git git, Ref tag) throws GitAPIException {
    git.checkout()
        .setName(tag.getName())
        .call();
    log.debug("Checked out repo {} with tag: {}", git.getRepository().getDirectory().getName(), tag.getName());
  }

  public static String normalizeVersion(String version) {
    // Define the pattern to match version numbers in the format "X.Y.Z" followed by any suffix
    Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+.*)");

    // Create a matcher for the input version string
    Matcher matcher = pattern.matcher(version);

    // Search for the pattern in the input string
    if (matcher.find()) {
      // Return the matched version part, including any suffixes
      return matcher.group(1);
    } else {
      // If no match, return the original version
      return version;
    }
  }
}
