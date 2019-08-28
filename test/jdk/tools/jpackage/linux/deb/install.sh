ARCH=$(dpkg --print-architecture)
sudo dpkg -i test_1.0-*_${ARCH}.deb
sudo dpkg -i fileassociationstest_1.0-*_${ARCH}.deb
sudo dpkg -i licensetest_1.0-*_${ARCH}.deb
sudo dpkg -i installdirtest_1.0-*_${ARCH}.deb
sudo dpkg -i jpackage-test-bundle-name_1.0-*_${ARCH}.deb
sudo dpkg -i maintainertest_1.0-*_${ARCH}.deb
sudo dpkg -i packagedepstestdep_1.0-*_${ARCH}.deb
sudo dpkg -i packagedepstest_1.0-*_${ARCH}.deb
