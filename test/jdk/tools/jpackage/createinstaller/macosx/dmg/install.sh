echo "Note: This script will install DMG files silently. In order to verify UI, each .dmg needs to launched manually via Finder."

# JPMacDmgTest
hdiutil attach JPMacDmgTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPMacDmgTest/JPMacDmgTest-1.0.pkg -target /
hdiutil detach /Volumes/JPMacDmgTest/

# JPMacDmgLicenseTest
hdiutil attach JPMacDmgLicenseTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPMacDmgLicenseTest/JPMacDmgLicenseTest-1.0.pkg -target /
hdiutil detach /Volumes/JPMacDmgLicenseTest/

# JPMacDmgAssociationsTest
hdiutil attach JPMacDmgAssociationsTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPMacDmgAssociationsTest/JPMacDmgAssociationsTest-1.0.pkg -target /
hdiutil detach /Volumes/JPMacDmgAssociationsTest/

# JPMacDmgOptionsTest
hdiutil attach JPMacDmgOptionsTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPMacDmgOptionsTest/JPMacDmgOptionsTest-1.0.pkg -target /
hdiutil detach /Volumes/JPMacDmgOptionsTest/

# JPMacDmgInstallDirTest
hdiutil attach JPMacDmgInstallDirTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPMacDmgInstallDirTest/JPMacDmgInstallDirTest-1.0.pkg -target /
hdiutil detach /Volumes/JPMacDmgInstallDirTest/
