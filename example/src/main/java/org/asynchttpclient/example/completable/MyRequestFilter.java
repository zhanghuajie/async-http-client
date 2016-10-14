package org.asynchttpclient.example.completable;

import org.asynchttpclient.filter.FilterContext;
import org.asynchttpclient.filter.FilterException;
import org.asynchttpclient.filter.RequestFilter;

/**
 * Created by zhanghuajie on 16/10/14.
 */
public class MyRequestFilter implements RequestFilter{
    @Override
    public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {
        return null;
    }
}
