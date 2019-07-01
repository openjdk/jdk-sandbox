echo "Note: This script will install DMG files silently. In order to verify UI, each .dmg needs to launched manually via Finder."

# Test
hdiutil attach Test-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/Test/Test-1.0.pkg -target /
hdiutil detach /Volumes/Test/

# LicenseTest
hdiutil attach LicenseTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/LicenseTest/LicenseTest-1.0.pkg -target /
hdiutil detach /Volumes/LicenseTest/

# AssociationsTest
hdiutil attach AssociationsTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/AssociationsTest/AssociationsTest-1.0.pkg -target /
hdiutil detach /Volumes/AssociationsTest/

# OptionsTest
hdiutil attach OptionsTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/OptionsTest/OptionsTest-1.0.pkg -target /
hdiutil detach /Volumes/OptionsTest/

# InstallDirTest
hdiutil attach InstallDirTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/InstallDirTest/InstallDirTest-1.0.pkg -target /
hdiutil detach /Volumes/InstallDirTest/
