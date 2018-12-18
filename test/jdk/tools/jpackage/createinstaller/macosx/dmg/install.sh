echo "Note: This script will install DMG files silently. In order to verify UI, each .dmg needs to launched manually via Finder."

# JPackageCreateInstallerTest
hdiutil attach JPackageCreateInstallerTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPackageCreateInstallerTest/JPackageCreateInstallerTest-1.0.pkg -target /
hdiutil detach /Volumes/JPackageCreateInstallerTest/

# JPackageCreateInstallerLicenseTest
hdiutil attach JPackageCreateInstallerLicenseTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPackageCreateInstallerLicenseTest/JPackageCreateInstallerLicenseTest-1.0.pkg -target /
hdiutil detach /Volumes/JPackageCreateInstallerLicenseTest/

# JPackageCreateInstallerFileAssociationsTest
hdiutil attach JPackageCreateInstallerFileAssociationsTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPackageCreateInstallerFileAssociationsTest/JPackageCreateInstallerFileAssociationsTest-1.0.pkg -target /
hdiutil detach /Volumes/JPackageCreateInstallerFileAssociationsTest/

# JPackageCreateInstallerInstallDirTest
hdiutil attach JPackageCreateInstallerInstallDirTest-1.0.dmg
sudo /usr/sbin/installer -pkg /Volumes/JPackageCreateInstallerInstallDirTest/JPackageCreateInstallerInstallDirTest-1.0.pkg -target /
hdiutil detach /Volumes/JPackageCreateInstallerInstallDirTest/
