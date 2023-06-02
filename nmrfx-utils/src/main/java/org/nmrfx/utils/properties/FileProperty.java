/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.nmrfx.utils.properties;

import javafx.beans.property.SimpleObjectProperty;

import java.io.File;

/**
 * @author brucejohnson
 */
public class FileProperty extends SimpleObjectProperty<File> {

    File file = null;

    @Override
    public File get() {
        return file;
    }

    @Override
    public void setValue(File file) {
        this.file = file;
        this.fireValueChangedEvent();
    }

    @Override
    public void set(File file) {
        this.file = file;
        this.fireValueChangedEvent();
    }

}
