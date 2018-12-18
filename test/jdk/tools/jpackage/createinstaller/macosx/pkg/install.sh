echo Note: This script will install packages silently. In order to verify UI, each .pkg needs to launched manually via Finder.
sudo /usr/sbin/installer -pkg JPackageCreateInstallerTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg JPackageCreateInstallerLicenseTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg JPackageCreateInstallerFileAssociationsTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg JPackageCreateInstallerInstallDirTest-1.0.pkg -target /
