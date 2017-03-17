#!/usr/bin/env python
#
# Release script for Hangr
#

import os
import sys
import shutil
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

#
# Globals
#

notes = File(".last-release-notes")
latestTag = git.Tag("latest-release")

def buildDefaultNotes(_):
    logParams = {
            'path': "latest-release..HEAD",
            'grep': ["Fix #", "Fixes #", "Closes #"],
            'pretty': "format:- %s"}
    logParams["invertGrep"] = True
    msgs = git.Log(**logParams).output()

    contents = ''

    lastReleaseDate = latestTag.get_created_date()
    closedIssues = github.find_issues(state='closed', since=lastReleaseDate)
    if closedIssues:
        contents += "Closed Issues:\n"
        for issue in closedIssues:
            contents += "- {title} (#{number})\n".format(
                    number=issue.number,
                    title=issue.title)

    if msgs: contents += "\nNotes:\n" + msgs
    return contents.strip()

def buildZipFile(zipPath, contents):
    # TODO move this into hostage, maybe?
    if "--dryrun" in sys.argv: return File(zipPath)

    if os.path.isdir(contents):
        if os.path.exists(zipPath):
            os.remove(zipPath)
        shutil.make_archive(zipPath.replace('.zip', ''), 'zip', contents)
    else:
        with ZipFile(zipPath, 'w') as zipObj:
            zipObj.write(contents)
    return File(zipPath)

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

# TODO build for linux?
verify(Execute('grunt cljsbuild-prod prepare-release release-mac release-win')).succeeds(silent=False)

buildName = "hangr-v%s-pre" % version
macFile = File("builds/%s/%s.dmg" % (buildName, buildName))
verify(macFile).exists()

winFolder = File("builds/%s/hangr-win32-x64" % (buildName))
verify(winFolder).exists()

# github doesn't allow .dmg directly
print "Compressing mac build..."
macZipPath = 'builds/hangr-macOS-%s.zip' % version
macZipFile = buildZipFile(macZipPath, macFile.path)
verify(macZipFile).exists()

# release-win outputs a folder
print "Compressing windows build..."
winZipPath = 'builds/hangr-win-x64-%s.zip' % version
winZipFile = buildZipFile(winZipPath, winFolder.path)
verify(winZipFile).exists()

#
# Upload to github
#

print "Uploading to Github..."

verify(versionTag).create()
verify(versionTag).push("origin")

gitRelease = github.Release(version)
verify(gitRelease).create(body=releaseNotes)

print "Uploading", macZipFile.path
verify(gitRelease).uploadFile(
        macZipFile.path, 'application/zip')

print "Uploading", winZipFile.path
verify(gitRelease).uploadFile(
        winZipFile.path, 'application/zip')

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
