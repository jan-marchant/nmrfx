/*
 * NMRFx Structure : A Program for Calculating Structures 
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

package org.nmrfx.chemistry.search;

import java.io.*;
import java.nio.file.Files;
import java.util.stream.Stream;

public class MBTree {

    static public void readBTree(String fileName) {
        try (Stream<String> lines = Files.lines(new File(fileName).toPath())) {
            lines.forEach(line -> {
                System.out.println(line);
            });
        } catch (FileNotFoundException fnf) {
            System.out.println("Cannot open the file " + fileName);
            System.out.println(fnf.getMessage());
        } catch (IOException ioe) {
            System.out.println("Cannot read the file " + fileName);
            System.out.println(ioe.getMessage());
        }
    }
}
