all: config-options.texinfo invariants-doc.texinfo html-chap html pdf

daikon.info: daikon.texinfo config-options.texinfo invariants-doc.texinfo 
# Large split size fixes an apparent bug in makeinfo 4.11:
# failing searches say
#   Search failed: "  Indirect:"
# instead of proper message.
	makeinfo --split-size=1000000 $<

developer.info: developer.texinfo 
	makeinfo $<

ifdef SKIP_JAVA
# If you know you haven't changed the Java code, or you don't care
# about the parts of the documentation that are automatically
# generated from it, running with SKIP_JAVA=1 will skip regenerating
# the code-derived texinfo, which in turn avoids recompiling all of
# Daikon, regenerating the splitter tests, and running the Daikon unit
# tests.
config-options.texinfo:
invariants-doc.texinfo:
else
config-options.texinfo: ../java/daikon/util/SimpleLog.class
	cd ../java && $(MAKE) ../doc/config-options.texinfo

invariants-doc.texinfo:
	cd ../java && $(MAKE) ../doc/invariants-doc.texinfo

# Always remake config-options.texinfo
# (We delegate to ../java/Makefile, which is more discerning)
.PHONY : config-options.texinfo
.PHONY : invariants-doc.texinfo
endif

../java/daikon/util/SimpleLog.class: ../java/daikon/util/SimpleLog.java
	make -C ../java

../java/daikon/util/SimpleLog.java:
	make -C ../java plume-copy

images/%.eps: images/%.dot
	dot -Tps $< >$@

# Create a jpg version of the dfepl flow, blow up by 4X to get cleaner
# output.
images/dfepl-flow.jpg: images/dfepl-flow.eps
	pstopnm -portrait -stdout -xsize 2000 $<  \
	  | pnmcrop \
	  | pnmscale 0.25 \
	  | pnmmargin -white 20 | pnmtojpeg > images/dfepl-flow.jpg

images/%.pdf: images/%.eps
	epstopdf $< --outfile=$@

html: daikon.html developer.html valgrind-merge.html

daikon.html: daikon.texinfo config-options.texinfo invariants-doc.texinfo images/dfepl-flow.jpg
	$(MAKE) daikon-html

# Just builds .html from .texinfo.
# Much faster than rebuilding the .texinfo files from Javadoc.
daikon-fast: daikon.texinfo
	$(MAKE) daikon-html

.PHONY: daikon-html
# Does the actual work
daikon-html:
	makeinfo --html --no-split daikon.texinfo
	# Fixup 'bad' href inserted by makeinfo.
	perl -pi -e 's|dir.html#Top|index.html|;' daikon.html
	../plume-lib/bin/add-favicon . daikon-favicon.png

developer.html: developer.texinfo 
	makeinfo --html --no-split $<
	# Fixup 'bad' href inserted by makeinfo.
	perl -pi -e 's|dir.html#Top|index.html|;' $@
	../plume-lib/bin/add-favicon . daikon-favicon.png

valgrind-merge.html: valgrind-merge.texinfo
	makeinfo --html --no-split $<
	# Fixup 'bad' href inserted by makeinfo.
	perl -pi -e 's|dir.html#Top|index.html|;' $@
	../plume-lib/bin/add-favicon . daikon-favicon.png

html-chap: daikon/index.html developer/index.html

# The "subdir" flag does not copy over images, so the
# pathnames become incorrect.  We need to copy them over ourselves.
daikon/index.html: daikon.texinfo config-options.texinfo invariants-doc.texinfo images/dfepl-flow.jpg
	rm -rf daikon/*
	makeinfo --html --split=chapter -o daikon $<
	# Fixup 'bad' href inserted by makeinfo.
	perl -pi -e 's|../dir/index.html|../index.html|;' daikon/index.html
	mkdir daikon/images
	cp images/*.png daikon/images
	cp images/*.jpg daikon/images
	../plume-lib/bin/add-favicon daikon ../daikon-favicon.png

developer/index.html: developer.texinfo
	rm -rf developer/*
	makeinfo --html --split=chapter -o developer $<
	# Fixup 'bad' href inserted by makeinfo.
	perl -pi -e 's|../dir/index.html|../index.html|;' developer/index.html
	# Fixup 'bad' href inserted by makeinfo.
	perl -pi -e 's|../valgrind-merge/index.html|../valgrind-merge.html|;' developer/Distribution.html
	mkdir developer/images
	cp images/*.png developer/images
	cp images/*.jpg developer/images
	../plume-lib/bin/add-favicon developer ../daikon-favicon.png

pdf: daikon.pdf images/dfepl-flow.eps developer.pdf valgrind-merge.pdf

daikon.pdf: daikon.texinfo config-options.texinfo invariants-doc.texinfo 
	makeinfo --pdf $<

developer.pdf: developer.texinfo
	makeinfo --pdf $<

valgrind-merge.pdf: valgrind-merge.texinfo
	makeinfo --pdf $<

# Note that the clean target does not remove invariants-doc.texinfo or
# config-options.texinfo.

clean: texi-clean
	rm -f daikon.tmp \
		daikon.info daikon.info-[0-9] \
		daikon.log daikon.dvi daikon.pdf daikon.html 
	rm -f *.bak
	rm -rf daikon/*
	rm -f developer.tmp \
		developer.info developer.info-[0-9] \
		developer.log developer.dvi developer.pdf developer.html 
	rm -rf developer/*
	rm -f images/dfepl-flow.{eps,pdf,jpg}
# Obsolete files
	rm -f daikon.ps developer.ps

texi-clean:
	rm -rf daikon.aux daikon.cp daikon.cps daikon.fn daikon.fns \
		   daikon.ky  daikon.pg daikon.toc daikon.tp daikon.vr
	rm -rf developer.aux developer.cp developer.cps developer.fn developer.fns \
		   developer.ky  developer.pg developer.toc developer.tp developer.vr

very-clean: clean
	rm -rf config-options.texinfo invariants-doc.texinfo
	rm -rf www/pubs

# Remakes entire distribution; use with caution.
dist:
	cd .. && $(MAKE) dist

# "www" target wouldn't work from here, because "www" is an (existing)
# directory; but go to the parent directory and run the rule from there.
# www:
# 	cd .. && $(MAKE) www

find-changes:
	diff -b -u -s --from-file /cse/web/research/plse/daikon/download/doc *.texinfo

