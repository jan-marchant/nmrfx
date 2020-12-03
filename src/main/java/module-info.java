module org.nmrfx.processor {
    requires org.nmrfx.core;
    exports org.nmrfx.processor.math;
    exports org.nmrfx.processor.datasets;
    exports org.nmrfx.processor.datasets.peaks;
    exports org.nmrfx.processor.datasets.vendor;
    exports org.nmrfx.processor.operations;
    exports org.nmrfx.processor.processing;
    exports org.nmrfx.processor.processing.processes;
    exports org.nmrfx.processor.optimization;
    exports org.nmrfx.processor.utilities;
    requires commons.math3;
    requires org.apache.commons.lang3;
    requires org.apache.commons.collections4;
    requires org.yaml.snakeyaml;
    requires smile.data;
    requires smile.core;
    requires smile.interpolation;
    requires smile.math;
    requires io.netty.all;
    requires java.logging;
    requires java.desktop;
    requires jdistlib;
    requires com.google.common;
    requires janino;
    requires commons.compiler;
    requires jython.slim;
}
