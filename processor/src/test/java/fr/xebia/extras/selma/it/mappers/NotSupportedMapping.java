package fr.xebia.extras.selma.it.mappers;

import fr.xebia.extras.selma.Mapper;

/**
 * Created with IntelliJ IDEA.
 * User: slemesle
 * Date: 22/11/2013
 * Time: 13:51
 * To change this template use File | Settings | File Templates.
 */
@Mapper(ignoreNotSupported = false, ignoreMissingProperties = false)
public interface NotSupportedMapping {

    String[][] map(String[][] in);

}