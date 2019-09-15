echo "Note: This script will install DMG files silently. In order to verify UI, each .dmg needs to launched manually via Finder."

# OptionsTest
hdiutil attach OptionsTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/OptionsTest/OptionsTest-1.0.pkg -target /
hdiutil detach /Volumes/OptionsTest/
