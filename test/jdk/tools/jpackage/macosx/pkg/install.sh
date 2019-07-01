echo Note: This script will install packages silently. In order to verify UI, each .pkg needs to launched manually via Finder.
sudo /usr/sbin/installer -pkg Test-1.0.pkg -target /
sudo /usr/sbin/installer -pkg LicenseTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg FileAssociationsTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg OptionsTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg InstallDirTest-1.0.pkg -target /
