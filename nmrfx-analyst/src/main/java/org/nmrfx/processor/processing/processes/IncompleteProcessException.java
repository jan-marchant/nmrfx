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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.processing.processes;

/**
 * Thrown with a message of what Operation threw an error and which Index that Operation is in a Process. Thrown when a
 * Process does not get to finish because an exception is thrown.
 *
 * @author johnsonb
 */
public class IncompleteProcessException extends Exception {

    public String op;
    public int index;

    public IncompleteProcessException(String message, String op, int index, StackTraceElement[] ste) {
        super(message);
        this.setStackTrace(ste);
        this.op = op;
        this.index = index;
    }
}
