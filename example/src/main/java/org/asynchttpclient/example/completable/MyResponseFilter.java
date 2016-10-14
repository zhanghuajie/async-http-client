package org.asynchttpclient.example.completable;

import org.asynchttpclient.filter.FilterContext;
import org.asynchttpclient.filter.FilterException;
import org.asynchttpclient.filter.ResponseFilter;
import org.asynchttpclient.util.HttpConstants;

/**
 * Created by zhanghuajie on 16/10/14.
 */
public class MyResponseFilter implements ResponseFilter {
    @Override
    public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {
        if (ctx.getResponseStatus().getStatusCode() != HttpConstants.ResponseStatusCodes.OK_200) {
            ctx.getRequest().getUrl();

        }

        return ctx;
    }
}
