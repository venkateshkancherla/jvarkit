##
## JFX applications
##

define fun_jfx1
	mkdir -p webstart/tmp/com/github/lindenb/jvarkit/tools/jfx/$(dir $(1))
	xsltproc \
		--xinclude \
		-o webstart/tmp/com/github/lindenb/jvarkit/tools/jfx/$(1).fxml \
		src/main/resources/xsl/webstart2jfxml.xsl \
		src/main/java/com/github/lindenb/jvarkit/tools/jfx/$(1).xml
	xsltproc \
		--xinclude \
		--stringparam name "$(notdir $(1))" \
		--stringparam codebase "${webstart.base}" \
		--stringparam mainclass "com.github.lindenb.jvarkit.tools.jfx.$(subst /,.,$(1))" \
		-o webstart/$(notdir $(1)).jnlp \
		src/main/resources/xsl/webstart2jnlp.xsl \
		src/main/java/com/github/lindenb/jvarkit/tools/jfx/$(1).xml
	xsltproc \
		--xinclude \
		--stringparam name "$(notdir $(1))" \
		src/main/resources/xsl/webstart2html.xsl \
		src/main/java/com/github/lindenb/jvarkit/tools/jfx/$(1).xml >> webstart/index.html ;
endef

define sign_jfx1
	jarsigner $(if ${FASTJARSIGN},,-tsa http://timestamp.digicert.com ) \
		-keystore .secret.keystore \
		-keypass "$(if ${keytool.keypass},${keytool.keypass},KEYTOOLPASS)" \
		-storepass "$(if ${keytool.storepass},${keytool.storepass},KEYTOOLSTOREPASS)" \
		"$(1)" secret ;
endef

define copy_rsrc_jfx1
	cp ./src/main/java/com/github/lindenb/jvarkit/jfx/components/FilesChooserPane.fxml \
		webstart/tmp/com/github/lindenb/jvarkit/jfx/components/ ;
	cp ./src/main/java/com/github/lindenb/jvarkit/jfx/components/FileChooserPane.fxml \
		webstart/tmp/com/github/lindenb/jvarkit/jfx/components/ ;
endef

ifneq (${gatk.jar},)	

PICARDJFX=$(addprefix picardjfx/,FilterVcfJfx GatherVcfsJfx FindMendelianViolationsJfx MergeVcfsJfx SortVcfsJfx )
GATKJFX=$(addprefix gatkjfx/,SelectVariantsJfx CombineVariantsJfx DepthOfCoverageJfx VariantAnnotatorJfx VariantFiltrationJfx VariantsToTableJfx SelectHeadersJfx VariantsToAllelicPrimitivesJfx LeftAlignAndTrimVariantsJfx)
SNPEFFJFX=$(addprefix snpeffjfx/,SnpEffJfx SnpSiftFilterJfx)

test-webstart: compile-webstart 
	#java -cp webstart/snpeffjfx.jar:webstart/SnpSift.jar com.github.lindenb.jvarkit.tools.jfx.snpeffjfx.SnpSiftFilterJfx
	java -cp webstart/gatkjfx.jar  com.github.lindenb.jvarkit.tools.jfx.gatkjfx.LeftAlignAndTrimVariantsJfx

scp-webstart: compile-webstart
	scp -r webstart/* "${webstart.remotedir}"

compile-webstart : .secret.keystore webstart/picard.jar webstart/SnpSift.jar \
	$(addprefix src/main/java/com/github/lindenb/jvarkit/tools/jfx/, $(addsuffix .java,${PICARDJFX} ${GATKJFX} ${SNPEFFJFX}) $(addsuffix .xml,${PICARDJFX} ${GATKJFX} ${SNPEFFJFX}))
	mkdir -p webstart/tmp
	echo "<html><body><table><tr><th>Name</th><th>Description</th></tr>" > webstart/index.html
	# compile snpeff tools
	mkdir -p webstart/tmp
	$(foreach P,${SNPEFFJFX},$(call fun_jfx1,$P))
	javac -d webstart/tmp -sourcepath  src/main/java $(addprefix src/main/java/com/github/lindenb/jvarkit/tools/jfx/, $(addsuffix .java,${SNPEFFJFX}))
	$(call copy_rsrc_jfx1)
	jar cvf webstart/snpeffjfx.jar -C webstart/tmp .
	rm -rf webstart/tmp
	# compile GATK tools
	mkdir -p $(dir $@)
	unzip -o "${gatk.jar}"  -d webstart/tmp
	$(foreach P,${GATKJFX},$(call fun_jfx1,$P))
	javac -d webstart/tmp -cp "${gatk.jar}" -sourcepath  src/main/java $(addprefix src/main/java/com/github/lindenb/jvarkit/tools/jfx/, $(addsuffix .java,${GATKJFX}))
	$(call copy_rsrc_jfx1)
	jar cvf webstart/gatkjfx.jar -C webstart/tmp .
	rm -rf webstart/tmp
	# compile picard tools
	mkdir -p webstart/tmp
	$(foreach P,${PICARDJFX},$(call fun_jfx1,$P))
	javac -d webstart/tmp -cp webstart/picard.jar -sourcepath  src/main/java $(addprefix src/main/java/com/github/lindenb/jvarkit/tools/jfx/, $(addsuffix .java,${PICARDJFX}))
	$(call copy_rsrc_jfx1)
	jar cvf webstart/picardjfx.jar -C webstart/tmp .
	rm -rf webstart/tmp
	# sign jars
	$(call sign_jfx1,webstart/picardjfx.jar)
	$(call sign_jfx1,webstart/snpeffjfx.jar)
	$(call sign_jfx1,webstart/gatkjfx.jar)
	$(call sign_jfx1,webstart/picard.jar)
	$(call sign_jfx1,webstart/snpEff.jar)
	echo "</table></body></html>" >> webstart/index.html
	chmod 755 webstart/*.html webstart/*.jar webstart/*.jnlp 
	

webstart/picard.jar:
	mkdir -p $(dir $@)
	wget -O "$@" "https://github.com/broadinstitute/picard/releases/download/2.8.1/$(notdir $@)"

webstart/SnpSift.jar : webstart/snpEff.jar
webstart/snpEff.jar :
	mkdir -p webstart
	wget -O "webstart/snpEff.zip" "http://heanet.dl.sourceforge.net//project/snpeff/snpEff_v4_2_core.zip"
	(cd webstart; unzip -o -j snpEff.zip snpEff/snpEff.jar snpEff/SnpSift.jar)
	rm webstart/snpEff.zip
	touch -c $@
	touch -c webstart/SnpSift.jar
	
	

endif






