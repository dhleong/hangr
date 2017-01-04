#!/usr/bin/env python
#
# Release script for Hangr
#

from zipfile import ZipFile

try:
    from hostage import *
except ImportError:
    print "!! Release library unavailable."
    print "!! Use `pip install hostage` to fix."
    print "!! You will also need an API token in .github.token,"
    print "!!  a .hubrrc config, or `brew install hub` configured."
    print "!! A $GITHUB_TOKEN env variable will also work."
    exit(1)


def buildDefaultNotes(_):
    logParams = {
            'path': "latest-release..HEAD",
            'grep': ["#bugfix", "#fix", "Fix"],
            'pretty': "format:- %s"}

    bugFixes = git.Log(**logParams).output()
    logParams["invertGrep"] = True
    msgs = git.Log(**logParams).output()

    contents = ''
    if bugFixes: contents += "Bug Fixes:\n" + bugFixes
    if msgs: contents += "\nNotes:\n" + msgs
    return contents.strip()

#
# Globals
#

notes = File(".last-release-notes")
latestTag = git.Tag("latest-release")

#
# Verify
#

version = verify(File("project.clj")
        .filtersTo(RegexFilter('defproject hangr "(.*)"'))
        ).valueElse(echoAndDie("No version!?"))
versionTag = git.Tag(version)

verify(versionTag.exists())\
    .then(echoAndDie("Version `%s` already exists!" % version))

#
# Make sure all the tests pass
#

verify(Execute("scripts/test.sh").succeeds(silent=False)).orElse(die())

#
# Build the release notes
#

contents = verify(notes.contents()).valueElse(buildDefaultNotes)
notes.delete()

verify(Edit(notes, withContent=contents).didCreate())\
        .orElse(echoAndDie("Aborted due to empty message"))

releaseNotes = notes.contents()

#
# Do the actual build
#

# TODO build for all platforms?
verify(Execute('grunt cljsbuild-prod prepare-release release-mac')).succeeds(silent=False)

buildName = "hangr-v%s-pre" % version
macFile = File("builds/%s/%s.dmg" % (buildName, buildName))
verify(macFile).exists()

#
# Upload to github
#

print "Uploading to Github..."

verify(versionTag).create()
verify(versionTag).push("origin")

gitRelease = github.Release(version)
verify(gitRelease).create(body=releaseNotes)

# github doesn't allow .dmg directly
print "Compressing mac build..."
macZipPath = 'builds/macOS-%s.zip' % version
with ZipFile(macZipPath, 'w') as macZip:
    macZip.write(macFile.path)
macZipFile = File(macZipPath)

print "Uploading", macZipFile.path
verify(macZipFile).exists()
verify(gitRelease).uploadFile(
        macZipFile.path, 'application/zip')

#
# Success! Now, do some bookkeeping
#

verify(latestTag).create(force=True)
verify(latestTag).push("origin", force=True)

#
# cleanup and done!
#

notes.delete()

print("Done! Published %s" % version)

# flake8: noqa
