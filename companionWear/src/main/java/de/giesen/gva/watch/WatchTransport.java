// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import android.content.Context;
import com.google.android.gms.wearable.*;
final class WatchTransport {
    static void request(Context context) {
        if(!WatchConsent.ready(context))return;
        Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(nodes->{
            for(Node node:nodes)if(node.isNearby())Wearable.getMessageClient(context)
                    .sendMessage(node.getId(),WatchSnapshot.REQUEST,new byte[]{2}).addOnFailureListener(e->{});
        }).addOnFailureListener(e->{});
    }
}
